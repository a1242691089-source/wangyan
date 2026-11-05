package com.huangder.lumibooks.ui.knowledge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.domain.model.KnowledgeEvidence
import com.huangder.lumibooks.domain.model.KnowledgeNodeDetail
import com.huangder.lumibooks.domain.model.KnowledgeNodeSummary
import com.huangder.lumibooks.domain.model.KnowledgeRelation
import com.huangder.lumibooks.domain.model.KnowledgeRelationType
import com.huangder.lumibooks.domain.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pure and unit-testable: where an evidence row can jump to in the reader.
 * Null when the row was saved without a selection (-1 sentinels) or when the
 * book is gone, in which case the UI disables the jump affordance.
 */
fun evidenceJumpTarget(evidence: KnowledgeEvidence): Pair<Int, Int>? =
    if (evidence.chapterIndex >= 0 && evidence.startPosition >= 0) {
        evidence.chapterIndex to evidence.startPosition
    } else {
        null
    }

/** Pure and unit-testable: picker rows for "add relation", minus the current node. */
fun filterRelationTargets(
    summaries: List<KnowledgeNodeSummary>,
    query: String,
    excludeNodeId: Long
): List<KnowledgeNodeSummary> {
    val trimmed = query.trim()
    return summaries.filter { summary ->
        summary.id != excludeNodeId && (
            trimmed.isEmpty() ||
                summary.title.contains(trimmed, ignoreCase = true) ||
                summary.thought.contains(trimmed, ignoreCase = true)
            )
    }
}

enum class KnowledgeDetailEvent { RELATION_EXISTS }

@HiltViewModel
class KnowledgeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: KnowledgeRepository
) : ViewModel() {

    /** Nav argument; -1 means the route was opened without one (renders as empty). */
    val nodeId: Long = savedStateHandle.get<Long>("nodeId") ?: -1L

    val detail: StateFlow<KnowledgeNodeDetail?> = repository.observeNodeDetail(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _targets = MutableStateFlow<List<KnowledgeNodeSummary>>(emptyList())
    val targets: StateFlow<List<KnowledgeNodeSummary>> = _targets.asStateFlow()

    private val _events = MutableSharedFlow<KnowledgeDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<KnowledgeDetailEvent> = _events.asSharedFlow()

    /** Loaded once when the add-relation sheet opens. */
    fun loadTargets() {
        viewModelScope.launch {
            _targets.value = repository.nodeSummaries().filter { it.id != nodeId }
        }
    }

    fun addRelation(targetNodeId: Long, type: KnowledgeRelationType) {
        val alreadyThere = detail.value?.relations?.any {
            it.counterpartNodeId == targetNodeId && it.relation.type == type
        } == true
        viewModelScope.launch {
            repository.addRelation(nodeId, targetNodeId, type)
            if (alreadyThere) _events.tryEmit(KnowledgeDetailEvent.RELATION_EXISTS)
        }
    }

    fun deleteRelation(relation: KnowledgeRelation) {
        viewModelScope.launch { repository.deleteRelation(relation) }
    }

    fun deleteNode(onDeleted: () -> Unit) {
        val node = detail.value?.node ?: return
        viewModelScope.launch {
            repository.deleteNode(node)
            onDeleted()
        }
    }
}
