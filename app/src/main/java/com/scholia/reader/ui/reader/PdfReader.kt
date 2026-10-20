package com.scholia.reader.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.scholia.reader.data.Highlight
import com.scholia.reader.pdf.NRect
import com.scholia.reader.pdf.PageText
import com.scholia.reader.pdf.PdfEngine
import com.scholia.reader.pdf.PdfLocation
import com.scholia.reader.ui.theme.HighlightColor
import kotlinx.coroutines.withTimeoutOrNull

/** Current text selection on a PDF page. [start]..[end] are inclusive pdfium char indices. */
data class PdfSelection(val page: Int, val start: Int, val end: Int, val text: String = "")

/** Callbacks from the page view up to the reader screen. */
class PdfCallbacks(
    val onSelection: (PdfSelection?) -> Unit,
    val onSelectingChanged: (Boolean) -> Unit,
    val onHighlightTap: (Highlight) -> Unit,
    val onTapEmpty: () -> Unit,
    /** The toolbar for a finished selection; drawn by the page so it can anchor to the text. */
    val toolbar: @Composable (selection: PdfSelection, anchor: IntRect, visibleArea: IntRect?) -> Unit,
)

@Composable
fun PdfReader(
    engine: PdfEngine,
    pagerState: PagerState,
    highlights: List<Highlight>,
    selection: PdfSelection?,
    selecting: Boolean,
    callbacks: PdfCallbacks,
    modifier: Modifier = Modifier,
) {
    // Group highlights by page once per change instead of per page per frame.
    val byPage = remember(highlights) {
        highlights.mapNotNull { h -> PdfLocation.decode(h.location)?.let { it to h } }
            .filter { it.first.start >= 0 }
            .groupBy({ it.first.page }, { it.first to it.second })
    }
    var zoomed by remember { mutableStateOf(false) }
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        // Don't let a selection drag or a pan of a zoomed page turn into a page swipe.
        userScrollEnabled = !selecting && !zoomed,
        key = { it },
        modifier = modifier,
    ) { page ->
        PdfPageView(
            engine = engine,
            page = page,
            highlights = byPage[page].orEmpty(),
            selection = selection?.takeIf { it.page == page },
            selecting = selecting,
            callbacks = callbacks,
            onZoomedChanged = { if (page == pagerState.currentPage) zoomed = it },
        )
    }
}

/** Inverts lightness so pages read as light text on the dark surface (#141312 .. #E6E1DF). */
private val NightFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.82f, 0f, 0f, 0f, 230f,
            0f, -0.82f, 0f, 0f, 225f,
            0f, 0f, -0.82f, 0f, 223f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private enum class Press { Tap, Cancel }

@Composable
private fun PdfPageView(
    engine: PdfEngine,
    page: Int,
    highlights: List<Pair<PdfLocation, Highlight>>,
    selection: PdfSelection?,
    selecting: Boolean,
    callbacks: PdfCallbacks,
    onZoomedChanged: (Boolean) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ratio = engine.aspectRatios[page]
        val boxRatio = maxWidth / maxHeight
        val pageW = if (ratio > boxRatio) maxWidth else maxHeight * ratio
        val pageH = if (ratio > boxRatio) maxWidth / ratio else maxHeight
        val widthPx = with(density) { pageW.roundToPx() }

        var scale by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        val isZoomed = scale > 1.01f
        LaunchedEffect(isZoomed) { onZoomedChanged(isZoomed) }
        // Re-render at 2x/3x once zoomed so text stays sharp instead of an upscaled bitmap.
        val renderScale = when {
            scale > 2.2f -> 3
            scale > 1.2f -> 2
            else -> 1
        }
        val boxWPx = constraints.maxWidth.toFloat()
        val boxHPx = constraints.maxHeight.toFloat()
        val pageWPx = with(density) { pageW.toPx() }
        val pageHPx = with(density) { pageH.toPx() }
        fun clampPan(p: Offset, s: Float) = Offset(
            p.x.coerceIn(-maxOf(0f, (pageWPx * s - boxWPx) / 2), maxOf(0f, (pageWPx * s - boxWPx) / 2)),
            p.y.coerceIn(-maxOf(0f, (pageHPx * s - boxHPx) / 2), maxOf(0f, (pageHPx * s - boxHPx) / 2)),
        )

        // Keep the last sharp bitmap on screen while a higher-resolution one renders.
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(page, widthPx, renderScale) {
            engine.render(page, widthPx * renderScale)?.let { bitmap = it }
        }
        val text: PageText? by produceState<PageText?>(null, page) { value = engine.text(page) }

        val highlightRects = remember(text, highlights) {
            val t = text ?: return@remember emptyList()
            highlights.map { (loc, h) -> h to t.rects(loc.start..loc.end) }
        }
        val selectionRects = remember(text, selection) {
            val t = text
            if (t == null || selection == null) emptyList() else t.rects(selection.start..selection.end)
        }

        val currentSelection by rememberUpdatedState(selection)
        val currentHighlights by rememberUpdatedState(highlightRects)
        val currentCallbacks by rememberUpdatedState(callbacks)
        // On-screen reading area (between the app bars); the toolbar is kept inside it.
        var viewport by remember { mutableStateOf<IntRect?>(null) }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewport = it.boundsInWindow().roundToIntRect() }
                // Taps in the margins around the page behave like taps on blank page areas.
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (currentSelection != null) currentCallbacks.onSelection(null) else currentCallbacks.onTapEmpty()
                    }
                }
                .pointerInput(pageWPx, pageHPx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val ev = awaitPointerEvent()
                            val fingers = ev.changes.count { it.pressed }
                            // Two fingers always zoom; one finger pans only when already zoomed
                            // and nothing nearer (a selection drag) has taken the event.
                            val panning = fingers == 1 && scale > 1.01f && ev.changes.none { it.isConsumed }
                            if (fingers >= 2 || panning) {
                                val newScale = (scale * ev.calculateZoom()).coerceIn(1f, 4f)
                                pan = if (newScale <= 1.01f) Offset.Zero else clampPan(pan + ev.calculatePan(), newScale)
                                scale = newScale
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
        Box(
            Modifier
                .size(pageW, pageH)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                }
                .background(if (dark) Color(0xFF1C1B1A) else Color.White),
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = "Page ${page + 1}",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = if (dark) NightFilter else null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp)
            }

            val selectionColor = MaterialTheme.colorScheme.primary
            val fills = HighlightColor.entries.associateWith { it.pageFill(dark) }
            val accents = HighlightColor.entries.associateWith { it.accent() }
            val blend = if (dark) BlendMode.Screen else BlendMode.Multiply
            val handleRadius = with(density) { 28.dp.toPx() }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(page, text) {
                        val t = text ?: return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            fun nx(o: Offset) = o.x / w
                            fun ny(o: Offset) = o.y / h
                            fun emit(s: Int, e: Int) = currentCallbacks.onSelection(
                                PdfSelection(page, s, e, t.text(s..e)),
                            )

                            // 1) Dragging a selection handle.
                            val sel = currentSelection
                            if (sel != null) {
                                val rs = t.rects(sel.start..sel.end)
                                val startPt = rs.firstOrNull()?.let { Offset(it.left * w, it.bottom * h) }
                                val endPt = rs.lastOrNull()?.let { Offset(it.right * w, it.bottom * h) }
                                val dStart = startPt?.let { (down.position - it).getDistance() } ?: Float.MAX_VALUE
                                val dEnd = endPt?.let { (down.position - it).getDistance() } ?: Float.MAX_VALUE
                                if (minOf(dStart, dEnd) < handleRadius) {
                                    val movingStart = dStart < dEnd
                                    var s = sel.start
                                    var e = sel.end
                                    down.consume()
                                    currentCallbacks.onSelectingChanged(true)
                                    drag(down.id) { change: PointerInputChange ->
                                        change.consume()
                                        // Aim slightly above the finger so the text isn't hidden under it.
                                        val p = change.position - Offset(0f, handleRadius * 0.6f)
                                        val i = t.nearestChar(nx(p), ny(p))
                                        if (i >= 0) {
                                            val w = t.wordAt(i)
                                            if (movingStart) s = minOf(w.first, e) else e = maxOf(w.last, s)
                                            emit(s, e)
                                        }
                                    }
                                    currentCallbacks.onSelectingChanged(false)
                                    return@awaitEachGesture
                                }
                            }

                            // 2) Tap, long-press, or a swipe that the pager takes over.
                            val press = withTimeoutOrNull<Press>(viewConfiguration.longPressTimeoutMillis) {
                                var result: Press? = null
                                while (result == null) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull { it.id == down.id }
                                    result = when {
                                        c == null -> Press.Cancel
                                        // Consume the tap so the margin tap handler below doesn't see it too.
                                        !c.pressed -> if (c.isConsumed) Press.Cancel else Press.Tap.also { c.consume() }
                                        c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop -> Press.Cancel
                                        else -> null
                                    }
                                }
                                result
                            }

                            when (press) {
                                null -> {
                                    // Long press: select the word, then extend while dragging.
                                    val i = t.charAt(nx(down.position), ny(down.position))
                                    if (i < 0) return@awaitEachGesture
                                    val word = t.wordAt(i)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentCallbacks.onSelectingChanged(true)
                                    emit(word.first, word.last)
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        c.consume()
                                        if (!c.pressed) break
                                        val j = t.nearestChar(nx(c.position), ny(c.position))
                                        if (j >= 0) {
                                            val w = t.wordAt(j)
                                            emit(minOf(word.first, w.first), maxOf(word.last, w.last))
                                        }
                                    }
                                    currentCallbacks.onSelectingChanged(false)
                                }
                                Press.Tap -> {
                                    val x = nx(down.position)
                                    val y = ny(down.position)
                                    val hit = currentHighlights.firstOrNull { (_, rects) -> rects.any { it.contains(x, y) } }
                                    when {
                                        hit != null -> currentCallbacks.onHighlightTap(hit.first)
                                        currentSelection != null -> currentCallbacks.onSelection(null)
                                        else -> currentCallbacks.onTapEmpty()
                                    }
                                }
                                Press.Cancel -> Unit
                            }
                        }
                    },
            ) {
                fun NRect.toPx() = Pair(
                    Offset(left * size.width, top * size.height),
                    Size((right - left) * size.width, (bottom - top) * size.height),
                )
                val pad = 1.5.dp.toPx()
                for ((h, rects) in highlightRects) {
                    val color = HighlightColor.fromKey(h.color)
                    val fill = fills.getValue(color)
                    for (r in rects) {
                        val (o, s) = r.toPx()
                        drawRoundRect(
                            fill,
                            topLeft = o - Offset(pad, pad),
                            size = Size(s.width + pad * 2, s.height + pad * 2),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            blendMode = blend,
                        )
                    }
                    val last = rects.lastOrNull()
                    if (last != null && !h.note.isNullOrBlank()) {
                        // A dot after the last word marks highlights that carry a note. (An
                        // underline would cut through the next line on tightly set pages.)
                        val r = 3.dp.toPx()
                        drawCircle(
                            accents.getValue(color),
                            radius = r,
                            center = Offset(last.right * size.width + pad + r * 1.6f, last.top * size.height + r + pad),
                        )
                    }
                }
                for (r in selectionRects) {
                    val (o, s) = r.toPx()
                    drawRect(selectionColor.copy(alpha = if (dark) 0.35f else 0.22f), topLeft = o, size = s)
                }
                if (selectionRects.isNotEmpty()) {
                    val first = selectionRects.first()
                    val last = selectionRects.last()
                    val stemW = 2.5.dp.toPx()
                    val knob = 6.dp.toPx()
                    // Start handle: stem up the first line, knob below.
                    val sx = first.left * size.width
                    drawRect(selectionColor, Offset(sx - stemW / 2, first.top * size.height), Size(stemW, (first.bottom - first.top) * size.height))
                    drawCircle(selectionColor, knob, Offset(sx, first.bottom * size.height + knob * 0.8f))
                    val ex = last.right * size.width
                    drawRect(selectionColor, Offset(ex - stemW / 2, last.top * size.height), Size(stemW, (last.bottom - last.top) * size.height))
                    drawCircle(selectionColor, knob, Offset(ex, last.bottom * size.height + knob * 0.8f))
                }
            }

            if (selection != null && !selecting && selectionRects.isNotEmpty()) {
                val wPx = with(density) { pageW.toPx() }
                val hPx = with(density) { pageH.toPx() }
                // The popup host is the zoomed page layer, whose on-screen bounds already include
                // the pan; only the scale needs applying to page-relative positions.
                fun tx(x: Float) = (x * wPx * scale).toInt()
                fun ty(y: Float) = (y * hPx * scale).toInt()
                val anchor = IntRect(
                    tx(selectionRects.minOf { it.left }),
                    ty(selectionRects.minOf { it.top }),
                    tx(selectionRects.maxOf { it.right }),
                    ty(selectionRects.maxOf { it.bottom }),
                )
                callbacks.toolbar(selection, anchor, viewport)
            }
        }
        }
    }
}
