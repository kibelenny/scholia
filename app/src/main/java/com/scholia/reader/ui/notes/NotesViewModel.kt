package com.scholia.reader.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scholia.reader.ScholiaApp
import com.scholia.reader.data.Book
import com.scholia.reader.data.Highlight
import com.scholia.reader.ui.theme.HighlightColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesState(
    val book: Book? = null,
    val all: List<Highlight> = emptyList(),
    val visible: List<Highlight> = emptyList(),
    val color: HighlightColor? = null,
    val query: String = "",
)

class NotesViewModel(app: ScholiaApp, bookId: Long) : ViewModel() {
    private val repo = app.repository
    private val color = MutableStateFlow<HighlightColor?>(null)
    private val query = MutableStateFlow("")

    val state: StateFlow<NotesState> = combine(
        repo.books.observe(bookId),
        repo.highlights.observeForBook(bookId),
        color,
        query,
    ) { book, all, color, query ->
        val visible = all.filter { h ->
            (color == null || HighlightColor.fromKey(h.color) == color) &&
                (query.isBlank() || h.selectedText.contains(query, true) || h.note.orEmpty().contains(query, true))
        }
        NotesState(book, all, visible, color, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesState())

    fun setColor(c: HighlightColor?) { color.value = c }
    fun setQuery(q: String) { query.value = q }

    fun save(h: Highlight, note: String, color: HighlightColor) = viewModelScope.launch {
        repo.highlights.update(h.copy(note = note.takeIf { it.isNotBlank() }, color = color.key))
    }

    fun delete(h: Highlight) = viewModelScope.launch { repo.highlights.delete(h.id) }

    /** Undo for [delete]: re-inserts with the same id so its position and date are kept. */
    fun restore(h: Highlight) = viewModelScope.launch { repo.highlights.insert(h) }
}
