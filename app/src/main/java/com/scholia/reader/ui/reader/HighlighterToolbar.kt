package com.scholia.reader.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.scholia.reader.ui.common.ColorDot
import com.scholia.reader.ui.theme.HighlightColor

/**
 * Floating "HIGHLIGHTER" toolbar from the design. [anchor] is the selection's bounding box in the
 * coordinate space of the composable that hosts this popup.
 */
@Composable
fun HighlighterToolbar(
    anchor: IntRect,
    current: HighlightColor,
    onHighlight: (HighlightColor) -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val container = if (dark) Color(0xFF2B2A28) else Color(0xFF2F2C28)
    val strip = if (dark) Color(0xFF363433) else Color(0xFF3A3632)
    val onContainer = Color(0xFFF4F0E8)
    val noteColor = if (dark) Color(0xFFE0884E) else Color(0xFFB35D35)
    val positioner = remember(anchor) { AboveOrBelow(anchor) }

    Popup(popupPositionProvider = positioner, properties = PopupProperties(focusable = false)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = container,
            contentColor = onContainer,
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 380.dp)
                .then(if (dark) Modifier.border(1.dp, Color(0xFF54433A), RoundedCornerShape(18.dp)) else Modifier),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "HIGHLIGHT",
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HighlightColor.entries.forEach { c ->
                            ColorDot(c, selected = c == current, onClick = { onHighlight(c) }, size = 28, ringColor = onContainer)
                        }
                    }
                    Spacer(Modifier.width(12.dp).weight(1f))
                    Surface(onClick = onNote, shape = RoundedCornerShape(50), color = noteColor, contentColor = Color.White) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Outlined.Notes, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Note", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.size(10.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(strip),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ToolbarAction(Icons.Outlined.ContentCopy, "Copy", onCopy, onContainer, Modifier.weight(1f))
                    ToolbarAction(Icons.Outlined.Share, "Share", onShare, onContainer, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelLarge)
    }
}

/** Centers the popup over the anchor, above it when there's room, otherwise below. */
private class AboveOrBelow(private val anchor: IntRect) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 24
        val abs = anchor.translate(anchorBounds.topLeft)
        val x = (abs.center.x - popupContentSize.width / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val above = abs.top - popupContentSize.height - margin
        val minTop = anchorBounds.top + margin
        val y = if (above >= minTop) above
        else (abs.bottom + margin * 2).coerceAtMost(windowSize.height - popupContentSize.height - margin)
        return IntOffset(x, y)
    }
}

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("Passage", text))
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, text: String, source: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "“$text”\n— $source")
    }
    context.startActivity(Intent.createChooser(send, null))
}
