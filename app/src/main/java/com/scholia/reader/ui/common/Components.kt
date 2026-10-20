package com.scholia.reader.ui.common

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scholia.reader.data.Book
import com.scholia.reader.data.Covers
import com.scholia.reader.ui.theme.Merriweather
import java.text.DateFormat
import java.util.Date

@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier, coverVersion: Any? = null) {
    val context = LocalContext.current
    val cover: ImageBitmap? by produceState<ImageBitmap?>(null, book.id, coverVersion) {
        value = Covers.load(context, book.id)?.asImageBitmap()
    }
    Box(modifier.clip(MaterialTheme.shapes.small)) {
        val c = cover
        if (c != null) {
            Image(c, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // Typographic placeholder in the design's warm tones.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimaryContainer),
                        ),
                    )
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    book.title,
                    color = Color(0xFFFDF9F1),
                    fontFamily = Merriweather,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun FormatBadge(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

fun relativeTime(time: Long): String {
    val now = System.currentTimeMillis()
    return when {
        now - time < DateUtils.MINUTE_IN_MILLIS -> "Just now"
        now - time < DateUtils.DAY_IN_MILLIS * 7 ->
            DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
    }
}
