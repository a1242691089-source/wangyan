package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.domain.model.KnowledgeCard
import com.huangder.lumibooks.domain.model.KnowledgeEvidence
import com.huangder.lumibooks.domain.model.KnowledgeNode
import com.huangder.lumibooks.domain.model.KnowledgeNodeDetail
import com.huangder.lumibooks.domain.model.KnowledgeNodeSummary
import com.huangder.lumibooks.domain.model.KnowledgeRelation
import com.huangder.lumibooks.domain.model.KnowledgeRelationType
import kotlinx.coroutines.flow.Flow

interface KnowledgeRepository {
    /** Nodes with their newest evidence, newest first; bookTitle is null when the book is gone. */
    fun observeKnowledgeCards(): Flow<List<KnowledgeCard>>

    /** Atomically inserts the node (syncId generated when blank) plus its evidence. */
    suspend fun saveNodeWithEvidence(node: KnowledgeNode, evidence: KnowledgeEvidence): Long

    /** Deletes the node; its evidence rows cascade. Records a sync tombstone. */
    suspend fun deleteNode(node: KnowledgeNode)

    /** Node, evidence and bidirectional relations; null while the node is gone. */
    fun observeNodeDetail(nodeId: Long): Flow<KnowledgeNodeDetail?>

    /** All nodes as picker rows, newest first. */
    suspend fun nodeSummaries(): List<KnowledgeNodeSummary>

    /**
     * Adds a directed edge. Returns the existing row id when the same
     * (source, target, type) triple already exists, so callers stay idempotent.
     * Throws IllegalArgumentException for a self-edge.
     */
    suspend fun addRelation(
        sourceNodeId: Long,
        targetNodeId: Long,
        type: KnowledgeRelationType
    ): Long

    /** Deletes the edge; records a knowledge-namespace sync tombstone. */
    suspend fun deleteRelation(relation: KnowledgeRelation)
}

