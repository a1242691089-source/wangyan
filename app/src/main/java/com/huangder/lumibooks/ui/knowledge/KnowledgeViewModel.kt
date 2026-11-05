package com.huangder.lumibooks.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.domain.model.KnowledgeCard
import com.huangder.lumibooks.domain.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Pure and unit-testable: tag filter, null = all cards. */
fun filterKnowledgeCards(cards: List<KnowledgeCard>, tag: String?): List<KnowledgeCard> =
    if (tag == null) cards else cards.filter { card -> tag in card.node.tags }

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

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag

    val visibleCards: StateFlow<List<KnowledgeCard>> =
        kotlinx.coroutines.flow.combine(cards, _selectedTag) { list, tag ->
            filterKnowledgeCards(list, tag)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTag(tag: String?) {
        _selectedTag.value = tag
    }
}
