package com.scholia.reader.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scholia.reader.ScholiaApp
import com.scholia.reader.data.Book
import com.scholia.reader.data.BookFormat
import com.scholia.reader.data.Covers
import com.scholia.reader.data.HighlightCount
import com.scholia.reader.data.LatestHighlight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Shelf(val label: String) { All("All Books"), Epub("ePubs"), Pdf("PDFs") }

data class HomeState(
    val loaded: Boolean = false,
    val allBooks: List<Book> = emptyList(),
    val shelfBooks: List<Book> = emptyList(),
    val counts: Map<Long, HighlightCount> = emptyMap(),
    val latest: LatestHighlight? = null,
    val shelf: Shelf = Shelf.All,
    val query: String = "",
    /** Bumped when a cover finishes generating so cover images reload. */
    val coverVersion: Int = 0,
)

class HomeViewModel(private val app: ScholiaApp) : ViewModel() {
    private val repo = app.repository
    private val shelf = MutableStateFlow(Shelf.All)
    private val query = MutableStateFlow("")
    private val coverVersion = MutableStateFlow(0)

    val state: StateFlow<HomeState> = combine(
        repo.books.observeRecent(),
        repo.highlights.observeCounts().map { list -> list.associateBy { it.bookId } },
        repo.highlights.observeLatest(),
        combine(shelf, query, ::Pair),
        coverVersion,
    ) { books, counts, latest, (shelf, query), version ->
        val filtered = books.filter { b ->
            (shelf == Shelf.All || (shelf == Shelf.Pdf) == (b.format == BookFormat.PDF)) &&
                (query.isBlank() || b.title.contains(query, true) || b.author.contains(query, true))
        }
        HomeState(true, books, filtered, counts, latest, shelf, query, version)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setShelf(s: Shelf) { shelf.value = s }
    fun setQuery(q: String) { query.value = q }

    fun import(uri: Uri, onReady: (Long) -> Unit) = viewModelScope.launch {
        val book = runCatching { repo.import(uri) }.getOrNull() ?: return@launch
        onReady(book.id)
        Covers.generate(app, book)
        coverVersion.value++
    }

    fun remove(book: Book) = viewModelScope.launch { repo.remove(app, book) }
}
