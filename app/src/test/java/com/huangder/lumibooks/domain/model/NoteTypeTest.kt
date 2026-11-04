package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * NoteType round-trip and legacy collapse rules. Storage keeps the raw string;
 * these tests pin the read-side derivation in [Note.noteType].
 */
class NoteTypeTest {

    // --- fromValue round-trip ---

    @Test
    fun `fromValue maps all six stored values`() {
        assertEquals(NoteType.EXCERPT, NoteType.fromValue("highlight"))
        assertEquals(NoteType.COMMENT, NoteType.fromValue("note"))
        assertEquals(NoteType.THOUGHT, NoteType.fromValue("thought"))
        assertEquals(NoteType.QUESTION, NoteType.fromValue("question"))
        assertEquals(NoteType.INSPIRATION, NoteType.fromValue("inspiration"))
        assertEquals(NoteType.AI_SUMMARY, NoteType.fromValue("ai_summary"))
    }

    @Test
    fun `round trip value to enum preserves each entry`() {
        NoteType.entries.forEach { type ->
            assertEquals(type, NoteType.fromValue(type.value))
        }
    }

    @Test
    fun `unknown value returns null`() {
        assertNull(NoteType.fromValue("something_future"))
        assertNull(NoteType.fromValue(""))
    }

    // --- Note.noteType derivation ---

    @Test
    fun `plain highlight without text is excerpt`() {
        val note = noteFixture(type = "highlight", noteText = "")
        assertEquals(NoteType.EXCERPT, note.noteType)
    }

    @Test
    fun `highlight with comment text collapses to comment`() {
        val note = noteFixture(type = "highlight", noteText = "我的批注")
        assertEquals(NoteType.COMMENT, note.noteType)
    }

    @Test
    fun `legacy note type collapses to comment`() {
        val note = noteFixture(type = "note", noteText = "旧版批注")
        assertEquals(NoteType.COMMENT, note.noteType)
    }

    @Test
    fun `underline with comment text is comment`() {
        val note = noteFixture(type = "underline", noteText = "划线批注")
        assertEquals(NoteType.COMMENT, note.noteType)
    }

    @Test
    fun `plain underline without text is not one of the six types`() {
        val note = noteFixture(type = "underline", noteText = "")
        assertNull(note.noteType)
    }

    @Test
    fun `ink strokes are not one of the six types`() {
        listOf(
            "pdf_ink_pen",
            "pdf_ink_highlighter",
            "epub_comic_ink_pen",
            "epub_comic_ink_highlighter"
        ).forEach { inkType ->
            assertNull("type=$inkType", noteFixture(type = inkType, noteText = "").noteType)
        }
    }

    @Test
    fun `new intent types map directly`() {
        assertEquals(NoteType.THOUGHT, noteFixture(type = "thought", noteText = "想法").noteType)
        assertEquals(NoteType.QUESTION, noteFixture(type = "question", noteText = "问题").noteType)
        assertEquals(NoteType.INSPIRATION, noteFixture(type = "inspiration", noteText = "灵感").noteType)
        assertEquals(NoteType.AI_SUMMARY, noteFixture(type = "ai_summary", noteText = "摘要").noteType)
    }

    @Test
    fun `unknown stored type renders without badge`() {
        assertNull(noteFixture(type = "something_future", noteText = "text").noteType)
    }

    private fun noteFixture(type: String, noteText: String): Note = Note(
        bookId = "book-1",
        chapterIndex = 0,
        startPosition = 0,
        endPosition = 10,
        selectedText = "selected",
        note = noteText,
        color = "#FFE9C46A",
        createdAt = 0L,
        type = type
    )
}
