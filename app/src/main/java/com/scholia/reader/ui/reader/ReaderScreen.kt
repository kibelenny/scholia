package com.scholia.reader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scholia.reader.app
import com.scholia.reader.data.BookFormat
import com.scholia.reader.pdf.PdfLocation
import com.scholia.reader.ui.common.NoteDraft
import com.scholia.reader.ui.common.NoteSheet
import com.scholia.reader.ui.common.locationLabel
import com.scholia.reader.ui.theme.HighlightColor
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(bookId: Long, jumpToHighlight: Long, onBack: () -> Unit, onOpenNotes: () -> Unit) {
    val context = LocalContext.current
    val vm: ReaderViewModel = viewModel(key = "reader-$bookId-$jumpToHighlight") {
        ReaderViewModel(context.app, bookId, jumpToHighlight)
    }
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val book = vm.book
    val title = book?.title.orEmpty()

    // Navigation callbacks the footer arrows use; set by whichever reader is showing.
    var goPrev by remember { mutableStateOf<(() -> Unit)?>(null) }
    var goNext by remember { mutableStateOf<(() -> Unit)?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = onOpenNotes) { Icon(Icons.AutoMirrored.Outlined.Notes, "Notes") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuBook, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            vm.positionLabel.ifBlank { " " },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        },
        bottomBar = {
            ReaderFooter(
                progress = vm.progress,
                label = vm.positionLabel,
                format = book?.format,
                onPrev = goPrev,
                onNext = goNext,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val doc = vm.doc) {
                ReaderDoc.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ReaderDoc.Failed -> FailedState(doc.message, onBack)
                is ReaderDoc.Pdf -> PdfContent(vm, doc, highlights, title, setNav = { p, n -> goPrev = p; goNext = n })
                is ReaderDoc.Epub -> EpubContent(vm, doc, highlights, title, setNav = { p, n -> goPrev = p; goNext = n })
            }
        }
    }

    vm.pending?.let { p ->
        NoteSheet(
            draft = p.draft,
            bookTitle = title,
            onSave = vm::savePending,
            onDismiss = { vm.pending = null },
        )
    }
    vm.editing?.let { h ->
        val format = book?.format ?: BookFormat.PDF
        NoteSheet(
            draft = NoteDraft(h.selectedText, h.locationLabel(format), HighlightColor.fromKey(h.color), h.note.orEmpty(), h.id),
            bookTitle = title,
            onSave = vm::saveEdit,
            onDismiss = { vm.editing = null },
            onDelete = { vm.delete(h) },
        )
    }
}

@Composable
private fun PdfContent(
    vm: ReaderViewModel,
    doc: ReaderDoc.Pdf,
    highlights: List<com.scholia.reader.data.Highlight>,
    title: String,
    setNav: (prev: () -> Unit, next: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val engine = doc.engine
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = doc.initialPage) { engine.pageCount }
    var selection by remember { mutableStateOf<PdfSelection?>(null) }
    var selecting by remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        setNav(
            { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
            { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(engine.pageCount - 1)) } },
        )
    }
    LaunchedEffect(pager.currentPage) {
        val page = pager.currentPage
        if (selection?.page != page) selection = null
        val count = engine.pageCount
        vm.savePosition(PdfLocation(page).encode(), (page + 1f) / count, "Page ${page + 1} of $count")
    }
    BackHandler(enabled = selection != null) { selection = null }

    PdfReader(
        engine = engine,
        pagerState = pager,
        highlights = highlights,
        selection = selection,
        selecting = selecting,
        callbacks = PdfCallbacks(
            onSelection = { selection = it },
            onSelectingChanged = { selecting = it },
            onHighlightTap = { vm.editing = it },
            onTapEmpty = {},
            toolbar = { sel, anchor ->
                val location = PdfLocation(sel.page, sel.start, sel.end).encode()
                HighlighterToolbar(
                    anchor = anchor,
                    current = vm.currentColor,
                    onHighlight = { c -> vm.addHighlight(location, sel.text, c); selection = null },
                    onNote = { vm.beginNote(location, sel.text, "Page ${sel.page + 1}"); selection = null },
                    onCopy = { copyText(context, sel.text); selection = null },
                    onShare = { shareText(context, sel.text, title); selection = null },
                )
            },
        ),
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun EpubContent(
    vm: ReaderViewModel,
    doc: ReaderDoc.Epub,
    highlights: List<com.scholia.reader.data.Highlight>,
    title: String,
    setNav: (prev: () -> Unit, next: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var selection by remember { mutableStateOf<Selection?>(null) }

    BackHandler(enabled = selection != null) { navigator?.clearSelection(); selection = null }

    Box(Modifier.fillMaxSize()) {
        EpubReader(
            publication = doc.publication,
            initialLocator = doc.initialLocator,
            highlights = highlights,
            onNavigator = { nav ->
                navigator = nav
                if (nav != null) setNav({ nav.goBackward(animated = true) }, { nav.goForward(animated = true) })
            },
            onLocator = { loc ->
                val chapter = loc.title?.takeIf { it.isNotBlank() }
                    ?: doc.publication.tableOfContents.firstOrNull { it.href.toString().substringBefore('#') == loc.href.toString() }?.title
                vm.savePosition(loc.toJSON().toString(), loc.locations.totalProgression?.toFloat() ?: 0f, chapter.orEmpty())
            },
            onSelection = { selection = it },
            onHighlightTap = { id -> highlights.firstOrNull { it.id == id }?.let { vm.editing = it } },
        )

        val sel = selection
        val nav = navigator
        if (sel != null && nav != null) {
            val r = sel.rect
            val anchor = if (r != null) IntRect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            else IntRect(0, 0, 0, 0)
            // Readium can't know the text until the selection settles, so re-read it at action time.
            fun act(block: (location: String, text: String, label: String) -> Unit) {
                scope.launch {
                    val current = nav.currentSelection() ?: return@launch
                    val text = current.locator.text.highlight.orEmpty().trim()
                    if (text.isNotEmpty()) block(current.locator.toJSON().toString(), text, current.locator.title.orEmpty())
                    nav.clearSelection()
                    selection = null
                }
            }
            HighlighterToolbar(
                anchor = anchor,
                current = vm.currentColor,
                onHighlight = { c -> act { loc, text, _ -> vm.addHighlight(loc, text, c) } },
                onNote = { act { loc, text, label -> vm.beginNote(loc, text, label) } },
                onCopy = { act { _, text, _ -> copyText(context, text) } },
                onShare = { act { _, text, _ -> shareText(context, text, title) } },
            )
        }
    }
}

@Composable
private fun ReaderFooter(progress: Float, label: String, format: BookFormat?, onPrev: (() -> Unit)?, onNext: (() -> Unit)?) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (format == BookFormat.PDF) label else "${(progress * 100).toInt()}% completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedIconButton(onClick = { onPrev?.invoke() }, enabled = onPrev != null, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.ChevronLeft, if (format == BookFormat.PDF) "Previous page" else "Previous chapter")
                    }
                    OutlinedIconButton(onClick = { onNext?.invoke() }, enabled = onNext != null, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.ChevronRight, if (format == BookFormat.PDF) "Next page" else "Next chapter")
                    }
                }
                if (format == BookFormat.PDF) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FailedState(message: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text("Back to library") }
    }
}
