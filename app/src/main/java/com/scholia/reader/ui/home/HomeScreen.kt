package com.scholia.reader.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scholia.reader.app
import com.scholia.reader.data.Book
import com.scholia.reader.data.BookFormat
import com.scholia.reader.data.LatestHighlight
import com.scholia.reader.ui.common.BookCover
import com.scholia.reader.ui.common.FormatBadge
import com.scholia.reader.ui.common.relativeTime
import com.scholia.reader.ui.theme.QuoteStyle

@Composable
fun HomeScreen(onOpenBook: (Long) -> Unit, onOpenNotes: (Long) -> Unit) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel { HomeViewModel(context.app) }
    val state by vm.state.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Book?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri, onOpenBook)
    }
    val launchPicker = { picker.launch(arrayOf("application/pdf", "application/epub+zip")) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = launchPicker,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Import Book", style = MaterialTheme.typography.titleSmall) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50),
            )
        },
    ) { padding ->
        val continueBook = state.allBooks.firstOrNull()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            fullWidth {
                TopBar(searching = searching, onToggleSearch = {
                    searching = !searching
                    if (!searching) vm.setQuery("")
                })
            }
            if (searching) {
                fullWidth { SearchField(state.query, vm::setQuery) }
            }

            if (state.loaded && state.allBooks.isEmpty()) {
                fullWidth { EmptyLibrary(onImport = launchPicker) }
                return@LazyVerticalGrid
            }

            fullWidth { ShelfChips(state, vm::setShelf) }

            if (continueBook != null && state.query.isBlank() && state.shelf == Shelf.All) {
                fullWidth { SectionHeader("Continue Reading", trailing = relativeTime(continueBook.lastOpened), dot = true) }
                fullWidth {
                    ContinueCard(
                        book = continueBook,
                        coverVersion = state.coverVersion,
                        onResume = { onOpenBook(continueBook.id) },
                        onNotes = { onOpenNotes(continueBook.id) },
                        onRemove = { confirmRemove = continueBook },
                    )
                }
            }

            fullWidth {
                SectionHeader(
                    "Recent Shelf",
                    subtitle = "${state.shelfBooks.size} ${if (state.shelfBooks.size == 1) "volume" else "volumes"}",
                )
            }
            items(state.shelfBooks, key = { it.id }) { book ->
                ShelfCard(
                    book = book,
                    highlightCount = state.counts[book.id]?.count ?: 0,
                    coverVersion = state.coverVersion,
                    onOpen = { onOpenBook(book.id) },
                    onNotes = { onOpenNotes(book.id) },
                    onRemove = { confirmRemove = book },
                )
            }
            if (state.shelfBooks.isEmpty()) {
                fullWidth {
                    Text(
                        "No books match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.latest?.let { latest ->
                if (state.query.isBlank()) fullWidth { QuoteCard(latest, onClick = { onOpenNotes(latest.bookId) }) }
            }
        }
    }

    confirmRemove?.let { book ->
        val count = state.counts[book.id]?.count ?: 0
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove from library?") },
            text = {
                Text(
                    "“${book.title}” will be removed from Folio" +
                        (if (count > 0) " along with its $count highlight${if (count == 1) "" else "s"} and notes" else "") +
                        ". The file itself stays on your device.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.remove(book); confirmRemove = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidth(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun TopBar(searching: Boolean, onToggleSearch: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "Folio",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleSearch) {
            Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, "Search")
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search titles, authors…") },
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

@Composable
private fun ShelfChips(state: HomeState, onSelect: (Shelf) -> Unit) {
    val counts = mapOf(
        Shelf.All to state.allBooks.size,
        Shelf.Epub to state.allBooks.count { it.format == BookFormat.EPUB },
        Shelf.Pdf to state.allBooks.count { it.format == BookFormat.PDF },
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Shelf.entries.forEach { s ->
            FilterChip(
                selected = state.shelf == s,
                onClick = { onSelect(s) },
                label = { Text("${s.label} (${counts[s]})", style = MaterialTheme.typography.labelLarge) },
                shape = RoundedCornerShape(50),
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null, trailing: String? = null, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (dot) {
            Box(
                Modifier
                    .padding(start = 6.dp, bottom = 10.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

private fun Book.percent() = "${(progress * 100).toInt()}%"

private fun Book.status() = when {
    progress >= 0.995f -> "Finished"
    progress > 0f || lastLocation.isNotBlank() -> "In progress"
    else -> "Not started"
}

@Composable
private fun ContinueCard(book: Book, coverVersion: Int, onResume: () -> Unit, onNotes: () -> Unit, onRemove: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Box(Modifier.width(104.dp).aspectRatio(0.68f)) {
                BookCover(book, Modifier.fillMaxSize(), coverVersion)
                FormatBadge(book.format.name, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildString {
                        append(book.status().uppercase())
                        if (book.positionLabel.isNotBlank()) append("  •  ").append(book.positionLabel)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(book.progress, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text(book.percent(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onResume, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Resume", maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    BookMenu(onNotes, onRemove) { open ->
                        FilledTonalIconButton(onClick = open, shape = MaterialTheme.shapes.small) {
                            Icon(Icons.Outlined.MoreVert, "More")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookMenu(onNotes: () -> Unit, onRemove: () -> Unit, anchor: @Composable (open: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        anchor { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Notes & highlights") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) },
                onClick = { open = false; onNotes() },
            )
            DropdownMenuItem(
                text = { Text("Remove from library") },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                onClick = { open = false; onRemove() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(
    book: Book,
    highlightCount: Int,
    coverVersion: Int,
    onOpen: () -> Unit,
    onNotes: () -> Unit,
    onRemove: () -> Unit,
) {
    BookMenu(onNotes, onRemove) { openMenu ->
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(onClick = onOpen, onLongClick = openMenu),
        ) {
            Column(Modifier.padding(12.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
                    BookCover(book, Modifier.fillMaxSize(), coverVersion)
                    FormatBadge(book.format.name, Modifier.align(Alignment.TopStart).padding(8.dp))
                    if (highlightCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Outlined.Notes, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("$highlightCount", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    book.author.ifBlank { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    Text(
                        book.status(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        book.percent(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(book.progress)
            }
        }
    }
}

@Composable
private fun QuoteCard(latest: LatestHighlight, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(20.dp)) {
            Icon(Icons.Outlined.FormatQuote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("“${latest.text}”", style = QuoteStyle, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(
                    "— ${latest.title}" + (if (latest.author.isNotBlank()) ", ${latest.author}" else "") +
                        " • Highlighted ${relativeTime(latest.createdAt).replaceFirstChar { it.lowercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Your shelf is empty", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Import a PDF or EPUB to start reading and taking notes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onImport, shape = RoundedCornerShape(50)) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Import Book")
        }
    }
}
