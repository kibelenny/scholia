package com.scholia.reader.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookRepository(
    private val db: AppDatabase,
    private val resolver: ContentResolver,
) {
    val books = db.books()
    val highlights = db.highlights()

    /**
     * Registers a document picked with ACTION_OPEN_DOCUMENT, keeping read access across reboots.
     * Returns the existing book if this URI was imported before.
     */
    suspend fun import(uri: Uri): Book = withContext(Dispatchers.IO) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        books.findByUri(uri.toString())?.let { return@withContext it }

        val displayName = displayName(uri)
        val mime = resolver.getType(uri).orEmpty()
        val format = when {
            mime == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true) -> BookFormat.PDF
            else -> BookFormat.EPUB
        }
        val book = Book(
            uri = uri.toString(),
            title = displayName.substringBeforeLast('.').ifBlank { "Untitled" },
            format = format,
        )
        book.copy(id = books.insert(book))
    }

    suspend fun remove(context: android.content.Context, book: Book) = withContext(Dispatchers.IO) {
        books.delete(book)
        Covers.delete(context, book.id)
        runCatching {
            resolver.releasePersistableUriPermission(
                Uri.parse(book.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun displayName(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
}
