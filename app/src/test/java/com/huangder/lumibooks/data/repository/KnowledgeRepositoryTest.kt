package com.huangder.lumibooks.data.repository

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.KnowledgeEvidence
import com.huangder.lumibooks.domain.model.KnowledgeNode
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
class KnowledgeRepositoryTest {
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

    private fun evidence(bookId: String = "book-1", createdAt: Long = 100) = KnowledgeEvidence(
        bookId = bookId, chapterIndex = 0, startPosition = 0, endPosition = 4,
        selectedText = "得意而忘言", createdAt = createdAt
    )

    private fun node(thought: String = "言不尽意", createdAt: Long = 100) = KnowledgeNode(
        thought = thought, tags = listOf("庄子"), createdAt = createdAt
    )

    private fun bookEntity(id: String, title: String) =
        com.huangder.lumibooks.data.local.entity.BookEntity(
            id = id, title = title, author = "", filePath = "/tmp/$id.epub", coverPath = null,
            format = "EPUB", lastReadTime = 0, readingProgress = 0f, createdAt = 0
        )

    @Test
    fun saveAndObserveRoundTrip() = runBlocking {
        db.bookDao().insertBook(bookEntity("book-1", "庄子"))
        repository.saveNodeWithEvidence(node(), evidence())
        val card = repository.observeKnowledgeCards().first().single()
        assertEquals("庄子", card.bookTitle)
        assertEquals("言不尽意", card.node.title)
        assertEquals("言不尽意", card.node.thought)
        assertEquals(listOf("庄子"), card.node.tags)
        assertEquals("得意而忘言", card.evidence.selectedText)
        assertTrue(card.node.syncId.isNotBlank())
        assertTrue(card.evidence.syncId.isNotBlank())
    }

    @Test
    fun titleDerivesFromFirstLineOnly() = runBlocking {
        repository.saveNodeWithEvidence(
            node(thought = "第一行理解\n第二行"), evidence()
        )
        assertEquals("第一行理解", repository.observeKnowledgeCards().first().single().node.title)
    }

    @Test
    fun cardsSortedByCreatedAtDescending() = runBlocking {
        repository.saveNodeWithEvidence(node(createdAt = 100), evidence(createdAt = 100))
        repository.saveNodeWithEvidence(node(thought = "新", createdAt = 200), evidence(createdAt = 200))
        assertEquals(
            listOf("新", "言不尽意"),
            repository.observeKnowledgeCards().first().map { it.node.thought }
        )
    }

    @Test
    fun bookTitleJoinsAndFallsBackToNull() = runBlocking {
        db.bookDao().insertBook(bookEntity("book-1", "庄子"))
        repository.saveNodeWithEvidence(node(), evidence(bookId = "book-1"))
        repository.saveNodeWithEvidence(
            node(thought = "失联", createdAt = 200), evidence(bookId = "gone", createdAt = 200)
        )
        val byTitle = repository.observeKnowledgeCards().first()
            .associate { it.node.thought to it.bookTitle }
        assertEquals("庄子", byTitle["言不尽意"])
        assertNull(byTitle["失联"])
    }

    @Test
    fun deleteNodeCascadesEvidenceAndWritesTombstone() = runBlocking {
        repository.saveNodeWithEvidence(node(), evidence())
        val card = repository.observeKnowledgeCards().first().single()
        repository.deleteNode(card.node)
        assertTrue(repository.observeKnowledgeCards().first().isEmpty())
        val tombstones = db.syncStateDao().getAllTombstones()
        assertTrue(tombstones.any { it.namespace == "knowledge" && it.itemId == card.node.syncId })
    }

    @Test
    fun blankSyncIdIsGeneratedDistinctly() = runBlocking {
        repository.saveNodeWithEvidence(node(), evidence())
        repository.saveNodeWithEvidence(node(thought = "另一条", createdAt = 200), evidence(createdAt = 200))
        val syncIds = repository.observeKnowledgeCards().first().map { it.node.syncId }
        assertEquals(2, syncIds.distinct().size)
    }
}
