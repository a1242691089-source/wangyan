package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.domain.model.KnowledgeCard
import com.huangder.lumibooks.domain.model.KnowledgeEvidence
import com.huangder.lumibooks.domain.model.KnowledgeNode
import kotlinx.coroutines.flow.Flow

interface KnowledgeRepository {
    /** Nodes with their newest evidence, newest first; bookTitle is null when the book is gone. */
    fun observeKnowledgeCards(): Flow<List<KnowledgeCard>>

    /** Atomically inserts the node (syncId generated when blank) plus its evidence. */
    suspend fun saveNodeWithEvidence(node: KnowledgeNode, evidence: KnowledgeEvidence): Long

    /** Deletes the node; its evidence rows cascade. Records a sync tombstone. */
    suspend fun deleteNode(node: KnowledgeNode)
}
