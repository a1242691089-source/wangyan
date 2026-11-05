package com.huangder.lumibooks.data.repository

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.KnowledgeEvidence
import com.huangder.lumibooks.domain.model.KnowledgeNode
import com.huangder.lumibooks.domain.model.KnowledgeRelationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KnowledgeRelationRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: KnowledgeRepositoryImpl

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KnowledgeRepositoryImpl(
            db.knowledgeDao(),
            BookRepositoryImpl(db.bookDao(), db.syncStateDao(), SyncIdentityStore(db.syncStateDao())),
            db.syncStateDao(), SyncIdentityStore(db.syncStateDao()), db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun evidence(createdAt: Long = 100) = KnowledgeEvidence(
        bookId = "book-1", chapterIndex = 0, startPosition = 0, endPosition = 4,
        selectedText = "得意而忘言", createdAt = createdAt
    )

    private suspend fun seedNode(thought: String, createdAt: Long = 100): Long =
        repository.saveNodeWithEvidence(
            KnowledgeNode(thought = thought, createdAt = createdAt),
            evidence(createdAt = createdAt)
        )

    @Test
    fun addRelationPersistsWithGeneratedSyncId() = runBlocking {
        val a = seedNode("甲")
        val b = seedNode("乙", createdAt = 200)
        val id = repository.addRelation(a, b, KnowledgeRelationType.EXPLAINS)
        assertTrue(id > 0)

        val detail = repository.observeNodeDetail(a).first()!!
        val view = detail.relations.single()
        assertEquals(KnowledgeRelationType.EXPLAINS, view.relation.type)
        assertEquals(true, view.isOutgoing)
        assertEquals(b, view.counterpartNodeId)
        assertEquals("乙", view.counterpartTitle)
        assertTrue(view.relation.syncId.isNotBlank())
    }

    @Test
    fun addRelationDuplicateTripleReturnsExistingId() = runBlocking {
        val a = seedNode("甲")
        val b = seedNode("乙", createdAt = 200)
        val first = repository.addRelation(a, b, KnowledgeRelationType.SUPPORTS)
        val second = repository.addRelation(a, b, KnowledgeRelationType.SUPPORTS)
        assertEquals(first, second)
        assertEquals(1, repository.observeNodeDetail(a).first()!!.relations.size)
    }

    @Test
    fun addRelationSamePairDifferentTypeIsAllowed() = runBlocking {
        val a = seedNode("甲")
        val b = seedNode("乙", createdAt = 200)
        repository.addRelation(a, b, KnowledgeRelationType.SUPPORTS)
        repository.addRelation(a, b, KnowledgeRelationType.EXTENDS)
        assertEquals(2, repository.observeNodeDetail(a).first()!!.relations.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun addRelationSelfReferenceThrows() {
        runBlocking {
            val a = seedNode("甲")
            repository.addRelation(a, a, KnowledgeRelationType.RELATED_TO)
        }
    }

    @Test
    fun deleteRelationWritesKnowledgeTombstone() = runBlocking {
        val a = seedNode("甲")
        val b = seedNode("乙", createdAt = 200)
        repository.addRelation(a, b, KnowledgeRelationType.CONTRADICTS)
        val relation = repository.observeNodeDetail(a).first()!!.relations.single().relation
        repository.deleteRelation(relation)

        assertTrue(repository.observeNodeDetail(a).first()!!.relations.isEmpty())
        val tombstones = db.syncStateDao().getAllTombstones()
        assertTrue(tombstones.any { it.namespace == "knowledge" && it.itemId == relation.syncId })
    }

    @Test
    fun deleteNodeCascadesItsRelations() = runBlocking {
        val a = seedNode("甲")
        val b = seedNode("乙", createdAt = 200)
        repository.addRelation(a, b, KnowledgeRelationType.EXTENDS)
        val nodeA = repository.observeNodeDetail(a).first()!!.node
        repository.deleteNode(nodeA)

        assertNull(repository.observeNodeDetail(a).first())
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM knowledge_relations").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun observeNodeDetailShowsIncomingRelationFromTargetSide() = runBlocking {
        val a = seedNode("甲")
        val b = seedNode("乙", createdAt = 200)
        repository.addRelation(a, b, KnowledgeRelationType.EXPLAINS)

        val fromB = repository.observeNodeDetail(b).first()!!.relations.single()
        assertEquals(false, fromB.isOutgoing)
        assertEquals(a, fromB.counterpartNodeId)
        assertEquals("甲", fromB.counterpartTitle)
        // Same edge id from both sides.
        assertEquals(
            repository.observeNodeDetail(a).first()!!.relations.single().relation.id,
            fromB.relation.id
        )
    }

    @Test
    fun nodeSummariesExposeThoughtForPicker() = runBlocking {
        seedNode("第一行\n第二行")
        val summary = repository.nodeSummaries().single()
        assertEquals("第一行", summary.title)
        assertEquals("第一行\n第二行", summary.thought)
    }
}
