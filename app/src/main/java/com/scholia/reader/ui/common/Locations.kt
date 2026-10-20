package com.scholia.reader.ui.common

import com.scholia.reader.data.BookFormat
import com.scholia.reader.data.Highlight
import com.scholia.reader.pdf.PdfLocation
import com.scholia.reader.ui.reader.toLocator

/** "Page 12" for PDFs; "Chapter title · 34%" for EPUBs. */
fun Highlight.locationLabel(format: BookFormat): String = when (format) {
    BookFormat.PDF -> PdfLocation.decode(location)?.let { "Page ${it.page + 1}" }.orEmpty()
    BookFormat.EPUB -> location.toLocator()?.let { loc ->
        val pct = loc.locations.totalProgression?.let { "${(it * 100).toInt()}%" }
        listOfNotNull(loc.title?.takeIf { it.isNotBlank() }, pct).joinToString(" · ")
    }.orEmpty()
}

/** Short label for the "Jump to …" button. */
fun Highlight.jumpLabel(format: BookFormat): String = when (format) {
    BookFormat.PDF -> PdfLocation.decode(location)?.let { "Jump to Page ${it.page + 1}" } ?: "Jump to passage"
    BookFormat.EPUB -> "Jump to passage"
}
