package com.huangder.lumibooks.data.backup

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.repository.ReadingRepositoryImpl
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.translation.TranslationTokenStore
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AnnotationPersistenceTest {
    @Test
    fun unusedTagsSurviveRestartAndSync() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "catalog.db"
        val preferences = DataStoreManager(context)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        fun manager() = PortableSnapshotManager(
            context, db, preferences, SyncIdentityStore(db.syncStateDao()),
            MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context)
        )
        fun reading() = ReadingRepositoryImpl(
            db.readingRecordDao(), db.bookmarkDao(), db.noteDao(), db.syncStateDao(),
            SyncIdentityStore(db.syncStateDao()), db
        )
        try {
            reading().createAnnotationTag("未使用")
            reading().insertNote(com.huangder.lumibooks.domain.model.Note(
                bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 2,
                selectedText = "正文", note = "", color = "yellow", createdAt = 100, tags = listOf("曾使用")
            ))
            val note = reading().getNotesByBookId("book").first().single()
            reading().updateNoteTags(note, emptyList())
            reading().deleteNote(note)
            val beforeRename = manager().capture(includeBookFiles = false).snapshot
            reading().renameAnnotationTag("未使用", "新名字")
            reading().createAnnotationTag("主动删除")
            reading().deleteAnnotationTag("主动删除")
            val saved = PortableSnapshot.fromJson(manager().capture(includeBookFiles = false).snapshot.toJson())
            assertEquals(setOf("曾使用", "新名字"), reading().observeAnnotationTags().first().toSet())
            val renamedAt = saved.annotationTags.single { it.name == "未使用" }.updatedAt
            manager().apply(beforeRename.copy(annotationTags = beforeRename.annotationTags.map {
                if (it.name == "未使用") it.copy(updatedAt = renamedAt) else it
            }), emptyMap(), replace = false)
            assertEquals(setOf("曾使用", "新名字"), reading().observeAnnotationTags().first().toSet())
            // Simulate sync with an old device which doesn't send the catalog at all.
            manager().apply(beforeRename.copy(annotationTags = emptyList()), emptyMap(), replace = false)
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals(setOf("曾使用", "新名字"), reading().observeAnnotationTags().first().toSet())
            val merged = PortableSnapshotMerger.merge(beforeRename, saved)
            assertEquals(saved.annotationTags.toSet(), merged.annotationTags.toSet())

            // A backup restore on a fresh database also restores unassigned labels.
            db.close()
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            manager().apply(saved, emptyMap(), replace = true, replaceAssetDirectories = false)
            assertEquals(setOf("曾使用", "新名字"), reading().observeAnnotationTags().first().toSet())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun syncKeepsNewTags() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "tags.db"
        val preferences = DataStoreManager(context)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        fun manager() = PortableSnapshotManager(
            context, db, preferences, SyncIdentityStore(db.syncStateDao()),
            MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context)
        )
        fun repository() = ReadingRepositoryImpl(
            db.readingRecordDao(), db.bookmarkDao(), db.noteDao(), db.syncStateDao(),
            SyncIdentityStore(db.syncStateDao()), db
        )
        try {
            db.noteDao().insertNote(NoteEntity(
                bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 2,
                selectedText = "正文", note = "笔记", color = "yellow", createdAt = 100,
                syncId = "note", updatedAt = 100
            ))
            db.bookmarkDao().insertBookmark(BookmarkEntity(
                bookId = "book", chapterIndex = 0, position = 0f, title = "书签",
                createdAt = 100, syncId = "bookmark", updatedAt = 100
            ))
            val inFlightSnapshot = manager().capture(includeBookFiles = false).snapshot
            val reading = repository()
            reading.updateNoteTags(reading.getNotesByBookId("book").first().single(), listOf("复习"))
            reading.updateBookmarkTags(reading.getBookmarksByBookId("book").first().single(), listOf("人物"))

            manager().apply(inFlightSnapshot, emptyMap(), replace = false)
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals(listOf("复习"), repository().getNotesByBookId("book").first().single().tags)
            assertEquals(listOf("人物"), repository().getBookmarksByBookId("book").first().single().tags)

            // An intentional, newer deletion from another device must still be applied.
            val newer = manager().capture(includeBookFiles = false).snapshot.let { snapshot ->
                snapshot.copy(
                    notes = snapshot.notes.map { it.copy(tagsJson = "[]", updatedAt = it.updatedAt + 1000) },
                    bookmarks = snapshot.bookmarks.map { it.copy(tagsJson = "[]", updatedAt = it.updatedAt + 1000) }
                )
            }
            manager().apply(newer, emptyMap(), replace = false)
            assertEquals(emptyList<String>(), repository().getNotesByBookId("book").first().single().tags)
            assertEquals(emptyList<String>(), repository().getBookmarksByBookId("book").first().single().tags)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun newNoteTypesPersistAndRoundTrip() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "notetypes.db"
        val preferences = DataStoreManager(context)
        var db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        fun manager() = PortableSnapshotManager(
            context, db, preferences, SyncIdentityStore(db.syncStateDao()),
            MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context)
        )
        fun repository() = ReadingRepositoryImpl(
            db.readingRecordDao(), db.bookmarkDao(), db.noteDao(), db.syncStateDao(),
            SyncIdentityStore(db.syncStateDao()), db
        )
        try {
            // Anchored thought (created from a selection).
            repository().insertNote(com.huangder.lumibooks.domain.model.Note(
                bookId = "book", chapterIndex = 3, startPosition = 5, endPosition = 9,
                selectedText = "正文", note = "我的思考", color = "yellow", createdAt = 100, type = "thought"
            ))
            // Unanchored question: sentinel -1 positions, empty selection.
            repository().insertNote(com.huangder.lumibooks.domain.model.Note(
                bookId = "book", chapterIndex = -1, startPosition = -1, endPosition = -1,
                selectedText = "", note = "一个疑问", color = "yellow", createdAt = 101, type = "question"
            ))
            // Ink strokes must stay excluded from the text-note stream.
            db.noteDao().insertNote(NoteEntity(
                bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 2,
                selectedText = "", note = "", color = "", createdAt = 102,
                type = "pdf_ink_pen", syncId = "ink-1", updatedAt = 102
            ))

            val textNotes = repository().observeTextNotes().first()
            assertEquals(setOf("thought", "question"), textNotes.map { it.type }.toSet())
            assertEquals(
                com.huangder.lumibooks.domain.model.NoteType.THOUGHT,
                textNotes.single { it.type == "thought" }.noteType
            )
            assertEquals(
                com.huangder.lumibooks.domain.model.NoteType.QUESTION,
                textNotes.single { it.type == "question" }.noteType
            )

            // Portable snapshot JSON round-trip preserves the raw type strings and
            // the sentinel -1 positions (WebDAV shares this serialization path).
            // The snapshot carries ink strokes too (existing sync behavior).
            val snapshot = manager().capture(includeBookFiles = false).snapshot
            val restored = PortableSnapshot.fromJson(snapshot.toJson())
            assertEquals(
                setOf("thought", "question", "pdf_ink_pen"),
                restored.notes.map { it.type }.toSet()
            )
            val question = restored.notes.single { it.type == "question" }
            assertEquals(-1, question.chapterIndex)
            assertEquals(-1, question.startPosition)
            assertEquals(-1, question.endPosition)

            // Restore on a fresh database; domain derivation survives the round trip.
            db.close()
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            manager().apply(restored, emptyMap(), replace = true, replaceAssetDirectories = false)
            val reloaded = repository().getNotesByBookId("book").first()
            assertEquals(
                com.huangder.lumibooks.domain.model.NoteType.QUESTION,
                reloaded.single { it.type == "question" }.noteType
            )
            assertEquals(-1, reloaded.single { it.type == "question" }.chapterIndex)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
