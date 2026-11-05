package com.huangder.lumibooks.data.repository

import androidx.room.withTransaction
import com.huangder.lumibooks.data.local.dao.KnowledgeDao
import com.huangder.lumibooks.data.local.dao.KnowledgeRelationRow
import com.huangder.lumibooks.data.local.dao.SyncStateDao
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.KnowledgeEvidenceEntity
import com.huangder.lumibooks.data.local.entity.KnowledgeNodeEntity
import com.huangder.lumibooks.data.local.entity.KnowledgeRelationEntity
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.AnnotationTags
import com.huangder.lumibooks.domain.model.KnowledgeCard
import com.huangder.lumibooks.domain.model.KnowledgeEvidence
import com.huangder.lumibooks.domain.model.KnowledgeNode
import com.huangder.lumibooks.domain.model.KnowledgeNodeDetail
import com.huangder.lumibooks.domain.model.KnowledgeNodeSummary
import com.huangder.lumibooks.domain.model.KnowledgeRelation
import com.huangder.lumibooks.domain.model.KnowledgeRelationType
import com.huangder.lumibooks.domain.model.KnowledgeRelationView
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.KnowledgeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class KnowledgeRepositoryImpl @Inject constructor(
    private val knowledgeDao: KnowledgeDao,
    private val bookRepository: BookRepository,
    private val syncStateDao: SyncStateDao,
    private val syncIdentityStore: SyncIdentityStore,
    private val database: AppDatabase
) : KnowledgeRepository {

    override fun observeKnowledgeCards(): Flow<List<KnowledgeCard>> =
        combine(knowledgeDao.observeNodesWithEvidence(), bookRepository.getAllBooks()) { rows, books ->
            val titles = books.associate { it.id to it.title }
            rows.map { row ->
                val node = row.node.toDomain()
                // Newest evidence represents the card; older ones stay in storage for future views.
                val evidence = row.evidences.maxByOrNull { it.createdAt }
                    ?: return@map null
                KnowledgeCard(node, evidence.toDomain(), titles[evidence.bookId])
            }.filterNotNull()
        }

    override suspend fun saveNodeWithEvidence(node: KnowledgeNode, evidence: KnowledgeEvidence): Long {
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val nodeId = knowledgeDao.insertNode(node.copy(
                syncId = node.syncId.ifBlank { UUID.randomUUID().toString() },
                // Title is derived once at write time so card rendering and future SQL filters stay stable.
                title = node.title.ifBlank {
                    node.thought.lineSequence().firstOrNull()?.trim()?.take(24).orEmpty()
                },
                tags = AnnotationTags.normalize(node.tags),
                createdAt = if (node.createdAt > 0) node.createdAt else now,
                updatedAt = now
            ).toEntity())
            knowledgeDao.insertEvidence(evidence.copy(
                nodeId = nodeId,
                syncId = evidence.syncId.ifBlank { UUID.randomUUID().toString() },
                createdAt = if (evidence.createdAt > 0) evidence.createdAt else now,
                updatedAt = now
            ).toEntity())
            nodeId
        }
    }

    override suspend fun deleteNode(node: KnowledgeNode) {
        val syncId = node.syncId.ifBlank { "legacy-knowledge-${node.id}" }
        syncStateDao.upsertTombstones(
            listOf(SyncTombstoneEntity("knowledge", syncId, System.currentTimeMillis(), syncIdentityStore.deviceId()))
        )
        knowledgeDao.deleteNode(node.toEntity())
    }

    override fun observeNodeDetail(nodeId: Long): Flow<KnowledgeNodeDetail?> =
        combine(
            knowledgeDao.observeNodeById(nodeId),
            knowledgeDao.observeEvidencesForNode(nodeId),
            knowledgeDao.observeRelationsForNode(nodeId)
        ) { node, evidences, relations ->
            node ?: return@combine null
            KnowledgeNodeDetail(
                node = node.toDomain(),
                evidences = evidences.map { it.toDomain() },
                relations = relations.map { it.toRelationView(nodeId) }
            )
        }

    override suspend fun nodeSummaries(): List<KnowledgeNodeSummary> =
        knowledgeDao.nodeSummaries().map {
            KnowledgeNodeSummary(id = it.id, title = it.title, thought = it.thought)
        }

    override suspend fun addRelation(
        sourceNodeId: Long,
        targetNodeId: Long,
        type: KnowledgeRelationType
    ): Long {
        require(sourceNodeId != targetNodeId) { "A knowledge node cannot relate to itself" }
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val inserted = knowledgeDao.insertRelation(
                KnowledgeRelationEntity(
                    sourceNodeId = sourceNodeId,
                    targetNodeId = targetNodeId,
                    syncId = UUID.randomUUID().toString(),
                    relationType = type.stored,
                    createdAt = now,
                    updatedAt = now
                )
            )
            if (inserted != -1L) {
                inserted
            } else {
                // The unique index rejected the triple; report the row that already exists.
                knowledgeDao.relationByTriple(sourceNodeId, targetNodeId, type.stored)?.id ?: -1L
            }
        }
    }

    override suspend fun deleteRelation(relation: KnowledgeRelation) {
        val syncId = relation.syncId.ifBlank { "legacy-relation-${relation.id}" }
        syncStateDao.upsertTombstones(
            listOf(SyncTombstoneEntity("knowledge", syncId, System.currentTimeMillis(), syncIdentityStore.deviceId()))
        )
        knowledgeDao.deleteRelationById(relation.id)
    }
}

private fun KnowledgeNodeEntity.toDomain() = KnowledgeNode(
    id = id, syncId = syncId, title = title, thought = thought,
    tags = AnnotationTags.decode(tagsJson), createdAt = createdAt, updatedAt = updatedAt
)

private fun KnowledgeEvidenceEntity.toDomain() = KnowledgeEvidence(
    id = id, nodeId = nodeId, syncId = syncId, bookId = bookId,
    chapterIndex = chapterIndex, chapterTitle = chapterTitle,
    startPosition = startPosition, endPosition = endPosition,
    startLocatorJson = startLocatorJson, endLocatorJson = endLocatorJson,
    selectedText = selectedText, noteSyncId = noteSyncId,
    createdAt = createdAt, updatedAt = updatedAt
)

private fun KnowledgeNode.toEntity() = KnowledgeNodeEntity(
    id = id, syncId = syncId, title = title, thought = thought,
    tagsJson = AnnotationTags.encode(tags), createdAt = createdAt, updatedAt = updatedAt
)

private fun KnowledgeEvidence.toEntity() = KnowledgeEvidenceEntity(
    id = id, nodeId = nodeId, syncId = syncId, bookId = bookId,
    chapterIndex = chapterIndex, chapterTitle = chapterTitle,
    startPosition = startPosition, endPosition = endPosition,
    startLocatorJson = startLocatorJson, endLocatorJson = endLocatorJson,
    selectedText = selectedText, noteSyncId = noteSyncId,
    createdAt = createdAt, updatedAt = updatedAt
)

private fun KnowledgeRelationRow.toRelationView(viewingNodeId: Long): KnowledgeRelationView {
    val isOutgoing = relation.sourceNodeId == viewingNodeId
    return KnowledgeRelationView(
        relation = KnowledgeRelation(
            id = relation.id,
            syncId = relation.syncId,
            sourceNodeId = relation.sourceNodeId,
            targetNodeId = relation.targetNodeId,
            type = KnowledgeRelationType.fromStored(relation.relationType),
            createdAt = relation.createdAt,
            updatedAt = relation.updatedAt
        ),
        isOutgoing = isOutgoing,
        counterpartNodeId = if (isOutgoing) relation.targetNodeId else relation.sourceNodeId,
        counterpartTitle = if (isOutgoing) targetTitle else sourceTitle
    )
}
