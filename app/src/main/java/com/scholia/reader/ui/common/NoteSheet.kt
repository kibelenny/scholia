package com.scholia.reader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scholia.reader.ui.theme.HighlightColor
import com.scholia.reader.ui.theme.QuoteStyle

/** What the note sheet is editing: a highlight about to be created, or an existing one. */
data class NoteDraft(
    val quote: String,
    val locationLabel: String,
    val color: HighlightColor,
    val note: String = "",
    /** Existing highlight id, or 0 for a new highlight. */
    val highlightId: Long = 0,
)

/** "Add Note to Highlight" bottom sheet from the design. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    draft: NoteDraft,
    bookTitle: String,
    onSave: (note: String, color: HighlightColor) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable(draft) { mutableStateOf(draft.note) }
    var color by remember(draft) { mutableStateOf(draft.color) }
    val focus = remember { FocusRequester() }
    val isNew = draft.highlightId == 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isNew) "Add Note to Highlight" else "Edit Note",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        listOf(bookTitle, draft.locationLabel).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close") }
            }

            Spacer(Modifier.height(16.dp))
            PassageExcerpt(draft.quote, color)

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Colour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HighlightColor.entries.forEach { c ->
                    ColorDot(c, selected = c == color, onClick = { color = c }, size = 26)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.Notes, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Personal Reflection", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    "${text.length} chars",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("What does this passage make you think?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .focusRequester(focus),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedBorderColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            if (text.isNotEmpty()) {
                TextButton(onClick = { text = "" }, modifier = Modifier.align(Alignment.End)) { Text("Clear") }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete highlight")
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Discard")
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(text.trim(), color) }, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Note")
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (isNew) runCatching { focus.requestFocus() } }
}

@Composable
fun PassageExcerpt(quote: String, color: HighlightColor, maxLines: Int = 8) {
    val accent = color.accent()
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.medium)
            .background(accent.copy(alpha = 0.10f)),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FormatQuote, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "PASSAGE EXCERPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                ColorChip(color)
            }
            Spacer(Modifier.height(8.dp))
            Text("“$quote”", style = QuoteStyle, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Quote with the colour bar on the left, as used on note cards. */
@Composable
fun PassageExcerptBlock(quote: String, color: HighlightColor) {
    val accent = color.accent()
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.small)
            .background(accent.copy(alpha = 0.10f)),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Text("“$quote”", style = QuoteStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

@Composable
fun ColorChip(color: HighlightColor) {
    val accent = color.accent()
    Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(5.dp))
            Text(color.label, style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

@Composable
fun ColorDot(color: HighlightColor, selected: Boolean, onClick: () -> Unit, size: Int = 30, ringColor: Color = MaterialTheme.colorScheme.onSurface) {
    val accent = color.accent()
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(accent)
            .then(if (selected) Modifier.border(2.dp, ringColor, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Outlined.Check, null, tint = Color(0xFF1C1C17), modifier = Modifier.size((size * 0.55f).dp))
    }
}
