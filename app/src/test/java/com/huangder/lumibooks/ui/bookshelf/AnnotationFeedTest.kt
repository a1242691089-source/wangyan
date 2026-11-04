package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.NoteType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AnnotationFeedTest {
    private fun book(id: String, title: String) = Book(id = id, title = title, author = "Author $id", filePath = "",
        coverPath = null, format = BookFormat.EPUB, lastReadTime = 0, readingProgress = 0f, createdAt = 0)

    private fun note(id: Long, bookId: String, createdAt: Long, type: String = "highlight", text: String = "text",
                     note: String = "", color: String = "red") =
        Note(id = id, bookId = bookId, chapterIndex = 0, startPosition = 0, endPosition = 4,
            selectedText = text, note = note, color = color, createdAt = createdAt, type = type)

    private val first = book("a", "First Book")
    private val second = book("b", "Second Book")
    private val highlight = note(1, "a", 100, text = "Some highlighted words")
    private val thought = note(2, "a", 200, text = "Passage", note = "My thought")
    private val underline = note(3, "b", 400, type = "underline", color = "blue")
    private val ruleHighlight = note(4, "b", 500, text = "rule match").copy(
        origin = Note.ORIGIN_HIGHLIGHT_RULE, sourceRuleId = "rule", sourceMatchKey = "key")
    private val ink = note(5, "a", 600, type = "pdf_ink_pen", text = "")
    private val orphan = note(6, "gone", 700)
    private val bookmark = Bookmark(id = 1, bookId = "b", chapterIndex = 2, position = 0f, title = "Chapter Mark",
        createdAt = 300, remark = "Remember this")

    private val feed = buildAnnotationFeed(listOf(first, second),
        listOf(highlight, thought, underline, ruleHighlight, ink, orphan), listOf(bookmark))

    private fun keys(entries: List<AnnotationFeedEntry>) = entries.map { it.key }

    @Test fun mergesBooksNewestFirstAndDropsInkAndOrphans() {
        assertEquals(listOf("note-4", "note-3", "bookmark-1", "note-2", "note-1"), keys(feed))
        assertEquals(second, feed.first().book)
    }

    @Test fun hidesRuleHighlightsUntilAskedAndCountsThemPerTab() {
        val query = AnnotationFeedQuery()
        assertEquals(listOf("note-3", "bookmark-1", "note-2", "note-1"), keys(feed.matching(query)))
        assertEquals(1, feed.ruleHighlightCount(query))
        assertEquals(0, feed.ruleHighlightCount(query.copy(type = AnnotationFeedType.NOTES)))
        val withRules = query.copy(type = AnnotationFeedType.HIGHLIGHTS, includeRuleHighlights = true)
        assertEquals(listOf("note-4", "note-1"), keys(feed.matching(withRules)))
    }

    @Test fun filtersByTypeAndStyle() {
        fun of(type: AnnotationFeedType, filter: ReadingMarkFilter = ReadingMarkFilter()) =
            keys(feed.matching(AnnotationFeedQuery(type = type, filter = filter)))
        assertEquals(listOf("note-1"), of(AnnotationFeedType.HIGHLIGHTS))
        assertEquals(listOf("note-3"), of(AnnotationFeedType.UNDERLINES))
        assertEquals(listOf("note-2"), of(AnnotationFeedType.NOTES))
        assertEquals(listOf("bookmark-1"), of(AnnotationFeedType.BOOKMARKS))
        assertEquals(emptyList<String>(), of(AnnotationFeedType.UNDERLINES, ReadingMarkFilter(color = "red")))
    }

    @Test fun searchesTextNotesRemarksAndBookTitlesIgnoringCase() {
        fun search(text: String) = keys(feed.matching(AnnotationFeedQuery(searchText = text)))
        assertEquals(listOf("note-1"), search("HIGHLIGHTED"))
        assertEquals(listOf("note-2"), search("my thought"))
        assertEquals(listOf("bookmark-1"), search("remember"))
        assertEquals(listOf("note-3", "bookmark-1"), search(" second "))
        assertEquals(keys(feed.matching(AnnotationFeedQuery())), search("   "))
    }

    @Test fun groupsByLocalDayKeepingOrder() {
        val day = LocalDate.of(2026, 7, 19)
        fun at(hour: Int, id: Long) = AnnotationFeedEntry.NoteEntry(first,
            note(id, "a", day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()))
        val groups = listOf(at(10, 1), at(8, 2)).plus(
            AnnotationFeedEntry.NoteEntry(first, note(3, "a", day.minusDays(1).atTime(23, 0).toInstant(ZoneOffset.UTC).toEpochMilli()))
        ).groupedByDay(ZoneOffset.UTC)
        assertEquals(listOf(day, day.minusDays(1)), groups.map { it.first })
        assertEquals(listOf("note-1", "note-2"), keys(groups.first().second))
    }

    // --- 六类型 noteType 筛选（正交于 tab 维度） ---

    private val intentFeed = buildAnnotationFeed(
        listOf(first, second),
        listOf(
            note(11, "a", 100, text = "原文", note = ""),                                  // EXCERPT
            note(12, "a", 200, text = "原文", note = "批注文字"),                            // COMMENT
            note(13, "a", 300, type = "note", text = "旧批注", note = "旧文字"),             // legacy → COMMENT
            note(14, "b", 400, type = "thought", text = "", note = "一条思考"),             // THOUGHT
            note(15, "b", 500, type = "question", text = "", note = "一个问题"),            // QUESTION
            note(16, "b", 600, type = "ai_summary", text = "", note = "AI 生成"),           // AI_SUMMARY
            note(17, "a", 700, type = "pdf_ink_pen", text = "")                            // ink，不入流
        ),
        listOf(Bookmark(id = 9, bookId = "b", chapterIndex = 1, position = 0f, title = "签", createdAt = 650))
    )

    @Test fun noteTypeFilterMatchesOnlyThatIntent() {
        fun of(type: NoteType?) = keys(intentFeed.matching(AnnotationFeedQuery(noteType = type)))
        assertEquals(listOf("note-16"), of(NoteType.AI_SUMMARY))
        assertEquals(listOf("note-14"), of(NoteType.THOUGHT))
        assertEquals(listOf("note-15"), of(NoteType.QUESTION))
        assertEquals(listOf("note-11"), of(NoteType.EXCERPT))
        // 批注 = legacy "note" + highlight/underline 带文字，两种存储归并到同一意图
        assertEquals(listOf("note-13", "note-12"), of(NoteType.COMMENT))
        // null = 不过滤（ink 在建流时已被剔除，书签保留）
        assertEquals(
            listOf("bookmark-9", "note-16", "note-15", "note-14", "note-13", "note-12", "note-11"),
            of(null)
        )
    }

    @Test fun noteTypeFilterExcludesBookmarksAndInk() {
        val query = AnnotationFeedQuery(noteType = NoteType.COMMENT)
        assertEquals(listOf("note-13", "note-12"), keys(intentFeed.matching(query)))
        val all = intentFeed.matching(AnnotationFeedQuery())
        assertTrue(all.none { it is AnnotationFeedEntry.NoteEntry && it.note.type == "pdf_ink_pen" })
    }

    @Test fun availableNoteTypesDeriveFromFeedNotesOnly() {
        val types = intentFeed.mapNotNull { (it as? AnnotationFeedEntry.NoteEntry)?.note?.noteType }
        assertEquals(
            setOf(NoteType.EXCERPT, NoteType.COMMENT, NoteType.THOUGHT, NoteType.QUESTION, NoteType.AI_SUMMARY),
            types.toSet()
        )
    }

    @Test fun unanchoredIntentNotesShowInFeedAndShareFallbackLabel() {
        val unanchored = note(21, "a", 800, type = "thought", text = "").copy(
            chapterIndex = -1, startPosition = -1, endPosition = -1)
        val feedWith = buildAnnotationFeed(listOf(first), listOf(unanchored), emptyList())
        assertEquals(listOf("note-21"), keys(feedWith.matching(AnnotationFeedQuery(noteType = NoteType.THOUGHT))))
        assertEquals(-1, (feedWith.single() as AnnotationFeedEntry.NoteEntry).note.chapterIndex)
    }
}
