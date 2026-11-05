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
import kotlinx.coroutines.flow.Flow

data class KnowledgeNodeWithEvidence(
    @Embedded val node: KnowledgeNodeEntity,
    @Relation(parentColumn = "id", entityColumn = "nodeId")
    val evidences: List<KnowledgeEvidenceEntity>
)

@Dao
interface KnowledgeDao {
    @Transaction
    @Query("SELECT * FROM knowledge_nodes ORDER BY createdAt DESC, id DESC")
    fun observeNodesWithEvidence(): Flow<List<KnowledgeNodeWithEvidence>>

    @Query("SELECT * FROM knowledge_evidence WHERE nodeId = :nodeId ORDER BY createdAt DESC, id DESC")
    suspend fun evidencesForNode(nodeId: Long): List<KnowledgeEvidenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: KnowledgeNodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: KnowledgeEvidenceEntity): Long

    @Delete
    suspend fun deleteNode(node: KnowledgeNodeEntity)
}
