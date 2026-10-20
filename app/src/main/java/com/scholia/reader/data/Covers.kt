package com.scholia.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import com.scholia.reader.app
import com.scholia.reader.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover
import java.io.File

/** Cover thumbnails cached as JPEGs in app storage, keyed by book id. */
object Covers {
    private const val WIDTH = 360
    private val memory = LruCache<Long, Bitmap>(24)

    private fun file(context: Context, bookId: Long) =
        File(context.filesDir, "covers/$bookId.jpg")

    suspend fun load(context: Context, bookId: Long): Bitmap? {
        memory.get(bookId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(context, bookId)
            if (!f.exists()) null else BitmapFactory.decodeFile(f.path)?.also { memory.put(bookId, it) }
        }
    }

    /**
     * Extracts the cover and any better metadata for a freshly imported book.
     * Failures are ignored: a missing cover just shows the placeholder.
     */
    suspend fun generate(context: Context, book: Book) = withContext(Dispatchers.IO) {
        val repo = context.app.repository
        val uri = Uri.parse(book.uri)
        val bitmap: Bitmap? = when (book.format) {
            BookFormat.PDF -> {
                runCatching {
                    PdfEngine.open(context, uri).use { engine ->
                        val (_, author) = engine.meta()
                        if (author.isNotBlank()) repo.books.updateMetadata(book.id, book.title, author)
                        engine.render(0, WIDTH)?.copy(Bitmap.Config.ARGB_8888, false)
                    }
                }.getOrNull()
            }
            BookFormat.EPUB -> {
                context.app.epubOpener.open(uri).getOrNull()?.let { pub ->
                    try {
                        val title = pub.metadata.title?.takeIf { it.isNotBlank() } ?: book.title
                        val author = pub.metadata.authors.joinToString { it.name }
                        repo.books.updateMetadata(book.id, title, author)
                        pub.cover()
                    } finally {
                        pub.close()
                    }
                }
            }
        }
        if (bitmap != null) {
            val scaled = if (bitmap.width > WIDTH) {
                Bitmap.createScaledBitmap(bitmap, WIDTH, (bitmap.height * WIDTH / bitmap.width.toFloat()).toInt(), true)
            } else bitmap
            val f = file(context, book.id)
            f.parentFile?.mkdirs()
            f.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            memory.put(book.id, scaled)
        }
    }

    fun delete(context: Context, bookId: Long) {
        memory.remove(bookId)
        file(context, bookId).delete()
    }
}
