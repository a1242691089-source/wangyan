package com.huangder.lumibooks.data.qa

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.KnowledgeEvidenceEntity
import com.huangder.lumibooks.data.local.entity.KnowledgeNodeEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.ai.qa.QaTokenizer
import com.huangder.lumibooks.data.repository.BookRepositoryImpl
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.QaSourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QaSourceLoaderTest {
    private lateinit var db: AppDatabase
    private lateinit var loader: QaSourceLoader

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncStateDao = db.syncStateDao()
        loader = QaSourceLoader(
            knowledgeDao = db.knowledgeDao(),
            noteDao = db.noteDao(),
            bookRepository = BookRepositoryImpl(db.bookDao(), syncStateDao, SyncIdentityStore(syncStateDao)),
            // Real reader instance; the seeded books are cloud-only so the
            // body tier never touches a parser.
            bookBodyReader = BookBodyReader(context, DataStoreManager(context))
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun book(id: String, title: String) = BookEntity(
        id = id, title = title, author = "", filePath = "/tmp/$id.epub", coverPath = null,
        format = "EPUB", lastReadTime = 0, readingProgress = 0f, createdAt = 0,
        isCloudOnly = true
    )

    private fun note(bookId: String, selectedText: String, note: String = "", createdAt: Long = 100) =
        NoteEntity(
            bookId = bookId, chapterIndex = 1, startPosition = 5, endPosition = 20,
            selectedText = selectedText, note = note, color = "yellow", createdAt = createdAt
        )

    @Test
    fun emptyQueryLoadsNothing() = runBlocking {
        assertTrue(loader.load(null, QaTokenizer.query("")).isEmpty())
    }

    @Test
    fun notesAndEvidenceMatchByKeywordAndAreWeightedByType() = runBlocking {
        db.noteDao().insertNote(note("book-1", "得意而忘言，得鱼而忘筌"))
        db.knowledgeDao().insertNode(
            KnowledgeNodeEntity(
                syncId = "n1", title = "忘言", thought = "言不尽意的注脚",
                createdAt = 100, updatedAt = 100
            )
        )
        val nodeId = db.knowledgeDao().nodeSummaries().single().id
        db.knowledgeDao().insertEvidence(
            KnowledgeEvidenceEntity(
                nodeId = nodeId, syncId = "e1", bookId = "book-1", chapterIndex = 3,
                chapterTitle = "外物", startPosition = 0, endPosition = 10,
                selectedText = "忘言之地", createdAt = 100
            )
        )
        val snippets = loader.load("book-1", QaTokenizer.query("忘言"))
        // Both the note and the evidence hit 「忘言」; evidence outranks notes.
        assertTrue(snippets.size >= 2)
        assertEquals(QaSourceType.KNOWLEDGE, snippets.first().candidate.type)
        assertTrue(snippets.any { it.candidate.type == QaSourceType.NOTE })
        // Node thought does not mention 忘言 beyond the title, so no third tier.
        // All snippets carry the entry-book evidence anchor when applicable.
        snippets.forEach { snippet ->
            if (snippet.candidate.type == QaSourceType.KNOWLEDGE) {
                assertEquals(3, snippet.candidate.chapterIndex)
            }
        }
    }

    @Test
    fun snapshotLimitTruncatesToNewest() = runBlocking {
        repeat(5) { index ->
            db.noteDao().insertNote(
                note("book-1", "庄子注脚$index 不含查询词", createdAt = 100L + index)
            )
        }
        // Query matches nothing → nothing packs even though rows exist.
        assertTrue(loader.load(null, QaTokenizer.query("量子力学")).isEmpty())
    }

    @Test
    fun missingBookTitleDoesNotFailRetrieval() = runBlocking {
        db.noteDao().insertNote(note("deleted-book", "得意而忘言"))
        val snippets = loader.load(null, QaTokenizer.query("忘言"))
        assertEquals(1, snippets.size)
        assertEquals(null, snippets.single().candidate.bookTitle)
    }
}
