package com.scholia.reader.ui.reader

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scholia.reader.ScholiaApp
import com.scholia.reader.data.Book
import com.scholia.reader.data.BookFormat
import com.scholia.reader.data.Highlight
import com.scholia.reader.pdf.PdfEngine
import com.scholia.reader.pdf.PdfLocation
import com.scholia.reader.ui.common.NoteDraft
import com.scholia.reader.ui.theme.HighlightColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

sealed interface ReaderDoc {
    data object Loading : ReaderDoc
    data class Failed(val message: String) : ReaderDoc
    data class Pdf(val engine: PdfEngine, val initialPage: Int) : ReaderDoc
    data class Epub(val publication: Publication, val initialLocator: Locator?) : ReaderDoc
}

/** A highlight waiting for the note sheet before it is saved. */
data class PendingHighlight(val location: String, val text: String, val draft: NoteDraft)

class ReaderViewModel(private val app: ScholiaApp, val bookId: Long, jumpToHighlight: Long) : ViewModel() {
    private val repo = app.repository

    var book by mutableStateOf<Book?>(null)
        private set
    var doc by mutableStateOf<ReaderDoc>(ReaderDoc.Loading)
        private set

    val highlights: StateFlow<List<Highlight>> =
        repo.highlights.observeForBook(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Last highlighter colour used; new highlights default to it. */
    var currentColor by mutableStateOf(HighlightColor.Amber)

    /** Note sheet state: a new highlight being annotated, or an existing one being edited. */
    var pending by mutableStateOf<PendingHighlight?>(null)
    var editing by mutableStateOf<Highlight?>(null)

    var positionLabel by mutableStateOf("")
        private set

    /** Zen mode: app and system bars hidden. Remembered across books and launches. */
    var zen by mutableStateOf(app.prefs.getBoolean(PREF_ZEN, false))
        private set

    fun setZenMode(on: Boolean) {
        zen = on
        app.prefs.edit().putBoolean(PREF_ZEN, on).apply()
    }

    /**
     * Latest EPUB position. The Readium view is rebuilt when the reader comes back from the notes
     * screen, and must restart here rather than at the position the book was opened with.
     */
    var epubLocator: Locator? = null
    var progress by mutableStateOf(0f)
        private set

    init {
        viewModelScope.launch {
            val b = repo.books.get(bookId)
            if (b == null) {
                doc = ReaderDoc.Failed("This book is no longer in your library.")
                return@launch
            }
            book = b
            positionLabel = b.positionLabel
            progress = b.progress
            repo.books.touch(bookId)
            val jump = if (jumpToHighlight > 0) repo.highlights.get(jumpToHighlight) else null
            val uri = Uri.parse(b.uri)
            doc = when (b.format) {
                BookFormat.PDF -> runCatching { PdfEngine.open(app, uri) }.fold(
                    onSuccess = { engine ->
                        val page = (jump?.let { PdfLocation.decode(it.location) } ?: PdfLocation.decode(b.lastLocation))
                            ?.page?.coerceIn(0, (engine.pageCount - 1).coerceAtLeast(0)) ?: 0
                        ReaderDoc.Pdf(engine, page)
                    },
                    onFailure = { ReaderDoc.Failed(openError(it)) },
                )
                BookFormat.EPUB -> app.epubOpener.open(uri).fold(
                    onSuccess = { pub ->
                        val loc = (jump?.location ?: b.lastLocation).toLocator()
                        ReaderDoc.Epub(pub, loc)
                    },
                    onFailure = { ReaderDoc.Failed(openError(it)) },
                )
            }
        }
    }

    private fun openError(e: Throwable): String =
        if (e is SecurityException || e is java.io.FileNotFoundException) {
            "Scholia can no longer access this file. It may have been moved or deleted — remove it and import it again."
        } else {
            "Couldn't open this book: ${e.message ?: e::class.simpleName}"
        }

    fun savePosition(location: String, progress: Float, label: String) {
        this.progress = progress
        positionLabel = label
        viewModelScope.launch { repo.books.updatePosition(bookId, location, progress, label) }
    }

    fun addHighlight(location: String, text: String, color: HighlightColor, note: String? = null) {
        currentColor = color
        viewModelScope.launch {
            repo.highlights.insert(
                Highlight(
                    bookId = bookId,
                    location = location,
                    selectedText = text,
                    note = note?.takeIf { it.isNotBlank() },
                    color = color.key,
                ),
            )
        }
    }

    fun beginNote(location: String, text: String, label: String) {
        pending = PendingHighlight(location, text, NoteDraft(text, label, currentColor))
    }

    fun savePending(note: String, color: HighlightColor) {
        pending?.let { addHighlight(it.location, it.text, color, note) }
        pending = null
    }

    fun saveEdit(note: String, color: HighlightColor) {
        val h = editing ?: return
        editing = null
        viewModelScope.launch {
            repo.highlights.update(h.copy(note = note.takeIf { it.isNotBlank() }, color = color.key))
        }
    }

    fun delete(h: Highlight) {
        editing = null
        viewModelScope.launch { repo.highlights.delete(h.id) }
    }

    override fun onCleared() {
        when (val d = doc) {
            is ReaderDoc.Pdf -> d.engine.close()
            is ReaderDoc.Epub -> d.publication.close()
            else -> Unit
        }
    }
}

private const val PREF_ZEN = "zen_mode"

fun String.toLocator(): Locator? =
    if (isBlank()) null else runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()
