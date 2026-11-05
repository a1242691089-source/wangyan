package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.huangder.lumibooks.data.local.entity.KnowledgeEvidenceEntity
import com.huangder.lumibooks.data.local.entity.KnowledgeNodeEntity
import com.huangder.lumibooks.data.local.entity.KnowledgeRelationEntity
import kotlinx.coroutines.flow.Flow

data class KnowledgeNodeWithEvidence(
    @Embedded val node: KnowledgeNodeEntity,
    @Relation(parentColumn = "id", entityColumn = "nodeId")
    val evidences: List<KnowledgeEvidenceEntity>
)

/** A relation row with both endpoint titles resolved, for the detail page. */
data class KnowledgeRelationRow(
    @Embedded val relation: KnowledgeRelationEntity,
    val sourceTitle: String,
    val targetTitle: String
)

/** How many relation edges a node participates in, as source or target. */
data class KnowledgeRelationCountRow(val nodeId: Long, val count: Int)

@Dao
interface KnowledgeDao {
    @Transaction
    @Query("SELECT * FROM knowledge_nodes ORDER BY createdAt DESC, id DESC")
    fun observeNodesWithEvidence(): Flow<List<KnowledgeNodeWithEvidence>>

    @Query("SELECT * FROM knowledge_evidence WHERE nodeId = :nodeId ORDER BY createdAt DESC, id DESC")
    suspend fun evidencesForNode(nodeId: Long): List<KnowledgeEvidenceEntity>

    @Query("SELECT * FROM knowledge_nodes WHERE id = :nodeId")
    fun observeNodeById(nodeId: Long): Flow<KnowledgeNodeEntity?>

    @Query("SELECT * FROM knowledge_evidence WHERE nodeId = :nodeId ORDER BY createdAt DESC, id DESC")
    fun observeEvidencesForNode(nodeId: Long): Flow<List<KnowledgeEvidenceEntity>>

    @Query("SELECT * FROM knowledge_nodes ORDER BY createdAt DESC, id DESC")
    suspend fun nodeSummaries(): List<KnowledgeNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: KnowledgeNodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: KnowledgeEvidenceEntity): Long

    @Delete
    suspend fun deleteNode(node: KnowledgeNodeEntity)

    /** Returns -1 when the (source, target, type) triple already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelation(relation: KnowledgeRelationEntity): Long

    @Query(
        "SELECT * FROM knowledge_relations WHERE sourceNodeId = :sourceNodeId " +
            "AND targetNodeId = :targetNodeId AND relationType = :relationType LIMIT 1"
    )
    suspend fun relationByTriple(
        sourceNodeId: Long,
        targetNodeId: Long,
        relationType: String
    ): KnowledgeRelationEntity?

    @Query("DELETE FROM knowledge_relations WHERE id = :id")
    suspend fun deleteRelationById(id: Long)

    @Query(
        "SELECT r.*, s.title AS sourceTitle, t.title AS targetTitle " +
            "FROM knowledge_relations r " +
            "JOIN knowledge_nodes s ON s.id = r.sourceNodeId " +
            "JOIN knowledge_nodes t ON t.id = r.targetNodeId " +
            "WHERE r.sourceNodeId = :nodeId OR r.targetNodeId = :nodeId " +
            "ORDER BY r.createdAt DESC, r.id DESC"
    )
    fun observeRelationsForNode(nodeId: Long): Flow<List<KnowledgeRelationRow>>

    /**
     * Edge count per node, counting both directions: A->B and B->A each add one.
     * Matches the detail page, which lists relations where the node is either end.
     */
    @Query(
        "SELECT nodeId, COUNT(*) AS count FROM (" +
            "SELECT sourceNodeId AS nodeId FROM knowledge_relations " +
            "UNION ALL " +
            "SELECT targetNodeId AS nodeId FROM knowledge_relations" +
            ") GROUP BY nodeId"
    )
    fun observeRelationCounts(): Flow<List<KnowledgeRelationCountRow>>
}
