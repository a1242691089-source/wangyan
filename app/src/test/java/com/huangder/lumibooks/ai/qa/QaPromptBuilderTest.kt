package com.huangder.lumibooks.ai.qa

import com.huangder.lumibooks.domain.model.QaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaPromptBuilderTest {
    private fun snippet(index: Int, text: String, type: QaSourceType = QaSourceType.BOOK_BODY) =
        SourceSnippet(
            index = index,
            candidate = com.huangder.lumibooks.ai.qa.SourceCandidate(
                type = type, text = text, bookId = "book-1", bookTitle = "庄子",
                chapterIndex = 2, chapterTitle = "养生主", startPosition = 10, endPosition = 40,
                createdAt = 100
            )
        )

    @Test
    fun systemPromptContainsAllHardConstraintsAndNumberedSnippets() {
        val prompt = QaPromptBuilder.systemPrompt(
            listOf(snippet(1, "第一段内容"), snippet(2, "第二段内容", QaSourceType.NOTE))
        )
        listOf(
            "只能使用片段中的信息", "标注来源编号", "提供的片段没有直接讲到这一点",
            "逐字照抄", "不要替作者下判断", "〔推断〕", "与提问相同的语言"
        ).forEach { constraint ->
            assertTrue("missing: $constraint", prompt.contains(constraint))
        }
        assertTrue(prompt.contains("[1]（书籍正文｜《庄子》第3章·养生主）第一段内容"))
        assertTrue(prompt.contains("[2]（我的笔记｜《庄子》第3章·养生主）第二段内容"))
    }

    @Test
    fun buildOrdersSystemHistoryThenQuestion() {
        val messages = QaPromptBuilder.build(
            question = "这本书说什么？",
            snippets = listOf(snippet(1, "片段")),
            history = listOf(QaTurn("旧问[1]", "旧答[2]"))
        )
        assertEquals(4, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertEquals("assistant", messages[2].role)
        assertEquals("user", messages[3].role)
        assertEquals("这本书说什么？", messages[3].content)
    }

    @Test
    fun historyMarkersAreStrippedBeforeReplay() {
        val messages = QaPromptBuilder.build(
            question = "新问题",
            snippets = emptyList(),
            history = listOf(QaTurn("旧问[1]", "旧答见[2]与[12]"))
        )
        assertFalse(messages[1].content.contains("[1]"))
        assertFalse(messages[2].content.contains("[2]"))
        assertFalse(messages[2].content.contains("[12]"))
        assertEquals("旧问", messages[1].content)
    }

    @Test
    fun historyTurnsAreTruncated() {
        val longAnswer = "答".repeat(1000)
        val messages = QaPromptBuilder.build("问", emptyList(), listOf(QaTurn("旧问", longAnswer)))
        assertEquals(400, messages[2].content.length)
    }

    @Test
    fun headersOmitMissingOriginParts() {
        val noOrigin = SourceSnippet(
            index = 1,
            candidate = com.huangder.lumibooks.ai.qa.SourceCandidate(
                type = QaSourceType.NODE_THOUGHT, text = "想法内容",
                chapterIndex = -1, startPosition = -1, titleHint = "节点标题", createdAt = 100
            )
        )
        assertEquals("[1]（我的想法｜「节点标题」）", QaPromptBuilder.snippetHeader(noOrigin))
        val bare = SourceSnippet(
            index = 2,
            candidate = com.huangder.lumibooks.ai.qa.SourceCandidate(
                type = QaSourceType.BOOK_BODY, text = "正文",
                chapterIndex = -1, startPosition = -1, createdAt = 100
            )
        )
        assertEquals("[2]（书籍正文）", QaPromptBuilder.snippetHeader(bare))
        val untitledChapter = SourceSnippet(
            index = 3,
            candidate = com.huangder.lumibooks.ai.qa.SourceCandidate(
                type = QaSourceType.BOOK_BODY, text = "正文", bookTitle = "庄子",
                chapterIndex = 0, startPosition = 0, createdAt = 100
            )
        )
        assertEquals("[3]（书籍正文｜《庄子》第1章）", QaPromptBuilder.snippetHeader(untitledChapter))
    }
}
