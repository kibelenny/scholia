package com.scholia.reader.ui.notes

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scholia.reader.app
import com.scholia.reader.data.Book
import com.scholia.reader.data.BookFormat
import com.scholia.reader.data.Highlight
import com.scholia.reader.ui.common.BookCover
import com.scholia.reader.ui.common.NoteDraft
import com.scholia.reader.ui.common.NoteSheet
import com.scholia.reader.ui.common.PassageExcerptBlock
import com.scholia.reader.ui.common.jumpLabel
import com.scholia.reader.ui.common.locationLabel
import com.scholia.reader.ui.common.relativeTime
import com.scholia.reader.ui.theme.HighlightColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(bookId: Long, onBack: () -> Unit, onJump: (highlightId: Long) -> Unit) {
    val context = LocalContext.current
    val vm: NotesViewModel = viewModel { NotesViewModel(context.app, bookId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Highlight?>(null) }
    val book = state.book

    fun deleteWithUndo(h: Highlight) {
        vm.delete(h)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Highlight deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.restore(h)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Notes", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (book != null) {
                item {
                    BookHeader(
                        book, state.all.size, state.all.count { !it.note.isNullOrBlank() },
                        onExport = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Notes — ${book.title}")
                                putExtra(Intent.EXTRA_TEXT, exportMarkdown(book, state.all))
                            }
                            context.startActivity(Intent.createChooser(send, "Export notes"))
                        },
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search within highlights & notes…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { ColorFilters(state, vm::setColor) }

            if (state.all.isEmpty()) {
                item { EmptyNotes() }
            } else if (state.visible.isEmpty()) {
                item {
                    Text(
                        "Nothing matches.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.visible, key = { it.id }) { h ->
                val dismiss = rememberSwipeToDismissBoxState()
                SwipeToDismissBox(
                    state = dismiss,
                    enableDismissFromStartToEnd = false,
                    onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) deleteWithUndo(h) },
                    backgroundContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    },
                    modifier = Modifier.animateItem(),
                ) {
                    NoteCard(
                        h = h,
                        format = book?.format ?: BookFormat.PDF,
                        onJump = { onJump(h.id) },
                        onEdit = { editing = h },
                        onDelete = { deleteWithUndo(h) },
                    )
                }
            }
        }
    }

    editing?.let { h ->
        NoteSheet(
            draft = NoteDraft(
                h.selectedText,
                h.locationLabel(book?.format ?: BookFormat.PDF),
                HighlightColor.fromKey(h.color),
                h.note.orEmpty(),
                h.id,
            ),
            bookTitle = book?.title.orEmpty(),
            onSave = { note, color -> vm.save(h, note, color); editing = null },
            onDismiss = { editing = null },
            onDelete = { editing = null; deleteWithUndo(h) },
        )
    }
}

@Composable
private fun BookHeader(book: Book, highlights: Int, notes: Int, onExport: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.width(76.dp).aspectRatio(0.7f))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        book.format.name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BookmarkBorder, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$highlights ${if (highlights == 1) "Highlight" else "Highlights"} • " +
                            "$notes ${if (notes == 1) "Note" else "Notes"}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (highlights > 0) {
                        TextButton(onClick = onExport, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.Outlined.IosShare, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorFilters(state: NotesState, onSelect: (HighlightColor?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.color == null,
            onClick = { onSelect(null) },
            label = { Text("All Notes (${state.all.size})") },
            shape = RoundedCornerShape(50),
            border = null,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
        HighlightColor.entries.forEach { c ->
            val n = state.all.count { HighlightColor.fromKey(it.color) == c }
            if (n > 0) {
                FilterChip(
                    selected = state.color == c,
                    onClick = { onSelect(if (state.color == c) null else c) },
                    label = { Text("${c.label} ($n)") },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent())) },
                    shape = RoundedCornerShape(50),
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NoteCard(h: Highlight, format: BookFormat, onJump: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = HighlightColor.fromKey(h.color)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.locationLabel(format).ifBlank { "Passage" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                com.scholia.reader.ui.common.ColorChip(color)
                Spacer(Modifier.weight(1f))
                Text(
                    relativeTime(h.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            PassageExcerptBlock(h.selectedText, color)
            if (!h.note.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(h.note, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(
                        if (h.note.isNullOrBlank()) Icons.AutoMirrored.Outlined.Notes else Icons.Outlined.Edit,
                        if (h.note.isNullOrBlank()) "Add note" else "Edit note",
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onJump,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(h.jumpLabel(format), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotes() {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.AutoMirrored.Outlined.Notes, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("No highlights yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Long-press text while reading to highlight it or add a note.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun exportMarkdown(book: Book, highlights: List<Highlight>): String = buildString {
    append("# ").append(book.title).append('\n')
    if (book.author.isNotBlank()) append("*").append(book.author).append("*\n")
    append('\n')
    for (h in highlights.sortedBy { it.createdAt }) {
        val where = h.locationLabel(book.format)
        if (where.isNotBlank()) append("**").append(where).append("**\n")
        append("> ").append(h.selectedText.replace("\n", "\n> ")).append('\n')
        if (!h.note.isNullOrBlank()) append('\n').append(h.note).append('\n')
        append('\n')
    }
}
