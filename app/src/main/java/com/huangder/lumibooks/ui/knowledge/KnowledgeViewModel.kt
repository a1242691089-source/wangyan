package com.huangder.lumibooks.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.domain.model.KnowledgeBookRef
import com.huangder.lumibooks.domain.model.KnowledgeCard
import com.huangder.lumibooks.domain.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/**
 * Pure and unit-testable: tag and book filters are ANDed, null means no
 * constraint on that axis (so the old two-argument call keeps its behaviour).
 */
fun filterKnowledgeCards(
    cards: List<KnowledgeCard>,
    tag: String?,
    bookId: String? = null
): List<KnowledgeCard> = cards.filter { card ->
    (tag == null || tag in card.node.tags) &&
        (bookId == null || card.evidence.bookId == bookId)
}

/**
 * Pure and unit-testable: books that own at least one card, title-sorted.
 * Books removed from the library keep their id but lose their title, so they
 * sort last instead of landing at the top under an empty name. Titles go through
 * a Collator because plain String order is code-point order, which reads as
 * random for Chinese book names.
 */
fun deriveKnowledgeBooks(cards: List<KnowledgeCard>): List<KnowledgeBookRef> {
    val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)
    return cards.map { KnowledgeBookRef(it.evidence.bookId, it.bookTitle) }
        .distinctBy { it.bookId }
        .sortedWith(compareBy(nullsLast(collator)) { it.title })
}

@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    knowledgeRepository: KnowledgeRepository
) : ViewModel() {

    val cards: StateFlow<List<KnowledgeCard>> = knowledgeRepository.observeKnowledgeCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tags actually in use, for the filter row. */
    val tags: StateFlow<List<String>> = cards.map { list ->
        list.flatMap { it.node.tags }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Books actually referenced by a card, for the second filter row. */
    val books: StateFlow<List<KnowledgeBookRef>> = cards.map { deriveKnowledgeBooks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag

    private val _selectedBookId = MutableStateFlow<String?>(null)
    val selectedBookId: StateFlow<String?> = _selectedBookId

    val visibleCards: StateFlow<List<KnowledgeCard>> =
        combine(cards, _selectedTag, _selectedBookId) { list, tag, bookId ->
            filterKnowledgeCards(list, tag, bookId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun setBook(bookId: String?) {
        _selectedBookId.value = bookId
    }

    /** Clears both axes; used by the "no match" empty state. */
    fun clearFilters() {
        _selectedTag.value = null
        _selectedBookId.value = null
    }
}
