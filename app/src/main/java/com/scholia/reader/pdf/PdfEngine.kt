package com.scholia.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable

/** Location of a highlight (or reading position when [start] < 0) inside a PDF. [end] is inclusive. */
@Serializable
data class PdfLocation(val page: Int, val start: Int = -1, val end: Int = -1) {
    fun encode(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun decode(s: String): PdfLocation? =
            if (s.isBlank()) null else runCatching { Json.decodeFromString(serializer(), s) }.getOrNull()
    }
}

/** A rectangle in page-relative coordinates: 0..1 on both axes, origin top-left. */
data class NRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerY get() = (top + bottom) / 2
    val isEmpty get() = right <= left || bottom <= top
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** Text layer of one page: one entry per pdfium char index. */
class PageText(val chars: String, val boxes: List<NRect?>) {
    val size get() = chars.length

    /** Char under (x, y); falls back to the nearest char within [tolerance] if none contains it. */
    fun charAt(x: Float, y: Float, tolerance: Float = 0.03f): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (i in boxes.indices) {
            val b = boxes[i] ?: continue
            if (b.contains(x, y)) return i
            val dx = maxOf(b.left - x, 0f, x - b.right)
            val dy = maxOf(b.top - y, 0f, y - b.bottom)
            val d = dx * dx + dy * dy * 4 // prefer the same line
            if (d < bestDist) { bestDist = d; best = i }
        }
        return if (bestDist <= tolerance * tolerance) best else -1
    }

    /** Nearest char regardless of distance; used while dragging a selection. */
    fun nearestChar(x: Float, y: Float): Int = charAt(x, y, tolerance = Float.MAX_VALUE / 8)

    fun wordAt(index: Int): IntRange {
        fun isWord(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-'
        if (index !in chars.indices || !isWord(chars[index])) return index..index
        var s = index
        var e = index
        while (s > 0 && isWord(chars[s - 1])) s--
        while (e < chars.lastIndex && isWord(chars[e + 1])) e++
        return s..e
    }

    fun text(range: IntRange): String {
        if (chars.isEmpty()) return ""
        val r = range.first.coerceIn(chars.indices)..range.last.coerceIn(chars.indices)
        return chars.substring(r.first, r.last + 1)
            .replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
            .replace(Regex("\\s+"), " ").trim()
    }

    /** One rect per line covering [range]. */
    fun rects(range: IntRange): List<NRect> {
        val out = ArrayList<NRect>()
        var cur: NRect? = null
        for (i in range) {
            val b = boxes.getOrNull(i) ?: continue
            val c = cur
            cur = if (c != null && b.centerY in c.top..c.bottom && b.left >= c.left - 0.01f) {
                NRect(minOf(c.left, b.left), minOf(c.top, b.top), maxOf(c.right, b.right), maxOf(c.bottom, b.bottom))
            } else {
                c?.let(out::add)
                b
            }
        }
        cur?.let(out::add)
        return out
    }
}

/**
 * Wraps a pdfium document opened from a content URI. All native calls go through one
 * serial dispatcher; pdfium is not safe to drive from several threads at once.
 */
class PdfEngine private constructor(
    private val pfd: ParcelFileDescriptor,
    private val doc: PdfDocument,
    /** width / height per page. */
    val aspectRatios: FloatArray,
) : Closeable {
    val pageCount get() = aspectRatios.size

    private val bitmaps = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val texts = LruCache<Int, PageText>(16)
    @Volatile private var closed = false

    suspend fun render(page: Int, widthPx: Int): Bitmap? = withContext(Pdfium) {
        if (closed || widthPx <= 0) return@withContext null
        val key = "$page@$widthPx"
        bitmaps.get(key)?.let { return@withContext it }
        val heightPx = (widthPx / aspectRatios[page]).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        doc.openPage(page)?.use { p ->
            p.renderPageBitmap(bmp, 0, 0, widthPx, heightPx, renderAnnot = true)
        } ?: return@withContext null
        bitmaps.put(key, bmp)
        bmp
    }

    suspend fun text(page: Int): PageText = withContext(Pdfium) {
        texts.get(page)?.let { return@withContext it }
        if (closed) return@withContext PageText("", emptyList())
        val result = doc.openPage(page)?.use { p ->
            p.openTextPage().use { t ->
                val n = t.textPageCountChars().coerceAtLeast(0)
                val raw = if (n > 0) t.textPageGetText(0, n).orEmpty() else ""
                val chars = if (raw.length == n) raw
                else buildString { for (i in 0 until n) append(t.textPageGetUnicode(i)) }
                val boxes = List(n) { i ->
                    val c = chars[i]
                    if (c == '\r' || c == '\n') return@List null
                    val box = t.textPageGetCharBox(i) ?: return@List null
                    val d = p.mapRectToDevice(0, 0, SCALE, SCALE, 0, box)
                    NRect(
                        minOf(d.left, d.right) / SCALE.toFloat(),
                        minOf(d.top, d.bottom) / SCALE.toFloat(),
                        maxOf(d.left, d.right) / SCALE.toFloat(),
                        maxOf(d.top, d.bottom) / SCALE.toFloat(),
                    ).takeUnless { it.isEmpty && c.isWhitespace() }
                }
                PageText(chars, boxes)
            }
        } ?: PageText("", emptyList())
        texts.put(page, result)
        result
    }

    /** Author/title from the document info dictionary (often missing or junk). */
    suspend fun meta(): Pair<String, String> = withContext(Pdfium) {
        val m = doc.getDocumentMeta()
        m.title.orEmpty().trim() to m.author.orEmpty().trim()
    }

    override fun close() {
        closed = true
        // Close on the pdfium thread so an in-flight render finishes first.
        kotlinx.coroutines.runBlocking(Pdfium) {
            runCatching { doc.close() }
            runCatching { pfd.close() }
        }
        bitmaps.evictAll()
    }

    companion object {
        private const val SCALE = 100_000

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val Pdfium = Dispatchers.IO.limitedParallelism(1)

        private var core: PdfiumCore? = null

        suspend fun open(context: Context, uri: Uri): PdfEngine = withContext(Pdfium) {
            val c = core ?: PdfiumCore(context.applicationContext).also { core = it }
            val pfd = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r")) {
                "Can't open $uri"
            }
            val doc = try {
                c.newDocument(pfd)
            } catch (e: Exception) {
                pfd.close()
                throw e
            }
            val sizes = doc.getPageSizes(72)
            val ratios = FloatArray(sizes.size) { i ->
                val s = sizes[i]
                if (s.width > 0 && s.height > 0) s.width.toFloat() / s.height else 0.707f
            }
            PdfEngine(pfd, doc, ratios)
        }

        /** Renders page 1 as a cover thumbnail. */
        suspend fun renderCover(context: Context, uri: Uri, widthPx: Int): Bitmap? =
            runCatching { open(context, uri).use { it.render(0, widthPx)?.copy(Bitmap.Config.ARGB_8888, false) } }.getOrNull()
    }
}
