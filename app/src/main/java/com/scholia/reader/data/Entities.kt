package com.scholia.reader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BookFormat { PDF, EPUB }

@Entity(tableName = "books", indices = [Index(value = ["uri"], unique = true)])
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val author: String = "",
    val format: BookFormat,
    /** PDF: [com.scholia.reader.pdf.PdfLocation] JSON. EPUB: Readium Locator JSON. Empty = start. */
    val lastLocation: String = "",
    val progress: Float = 0f,
    /** Human-readable position, e.g. "Page 184 of 270" or the current chapter title. */
    val positionLabel: String = "",
    val lastOpened: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** PDF: page + char range JSON. EPUB: Readium Locator JSON. */
    val location: String,
    val selectedText: String,
    val note: String?,
    val color: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

data class HighlightCount(val bookId: Long, val count: Int, val notes: Int)

data class LatestHighlight(val bookId: Long, val text: String, val title: String, val author: String, val createdAt: Long)
