package com.huangder.lumibooks.ai.qa

import com.huangder.lumibooks.ai.AiMessage
import com.huangder.lumibooks.domain.model.QaSourceType

/**
 * Builds the QA prompt. The system rules are the plan's hard constraints and
 * are kept verbatim in Chinese regardless of the user's language; rule 7 asks
 * the model to answer in the language of the question.
 */
object QaPromptBuilder {
    private const val MAX_TURN_CHARS = 400

    private val historyCitation = Regex("\\[\\d{1,2}]")

    private val sourceTypeLabels = mapOf(
        QaSourceType.BOOK_BODY to "书籍正文",
        QaSourceType.NOTE to "我的笔记",
        QaSourceType.KNOWLEDGE to "知识证据",
        QaSourceType.NODE_THOUGHT to "我的想法"
    )

    fun systemPrompt(snippets: List<SourceSnippet>): String = buildString {
        appendLine("你是「读书问答」助手。只能依据下面编号片段回答，以下规则不可违反：")
        appendLine("1. 只能使用片段中的信息，不得引入片段以外的知识。")
        appendLine("2. 每个结论句末尾必须标注来源编号，格式如 [1]；多来源写 [1][2]。")
        appendLine("3. 片段不足以回答时，直接说「提供的片段没有直接讲到这一点」，不要猜测、不要补全、不要用常识填充。")
        appendLine("4. 引用原文必须逐字照抄片段，不得改写、不得省略中间内容，用「」包裹。")
        appendLine("5. 不要替作者下判断（不要写「作者认为…」「作者想表达…」），只陈述片段里写了什么。")
        appendLine("6. 任何超出片段的推论，必须在该句句首标注〔推断〕。")
        appendLine("7. 用与提问相同的语言回答；只输出回答正文，不要解释规则。")
        appendLine("片段：")
        snippets.forEach { snippet ->
            append(snippetHeader(snippet))
            append(snippet.candidate.text)
            appendLine()
        }
    }.trimEnd()

    fun build(question: String, snippets: List<SourceSnippet>, history: List<QaTurn>): List<AiMessage> {
        val messages = mutableListOf<AiMessage>()
        messages.add(AiMessage.system(systemPrompt(snippets)))
        history.forEach { turn ->
            // Strip old [n] markers: every question re-retrieves and renumbers,
            // so replayed markers would point at numbers that no longer exist.
            messages.add(AiMessage.user(stripMarkers(turn.question)))
            messages.add(AiMessage.assistant(stripMarkers(turn.answer)))
        }
        messages.add(AiMessage.user(question))
        return messages
    }

    internal fun snippetHeader(snippet: SourceSnippet): String {
        val typeLabel = sourceTypeLabels.getValue(snippet.candidate.type)
        val book = snippet.candidate.bookTitle?.let { "《$it》" }.orEmpty()
        val chapter = when {
            snippet.candidate.chapterIndex < 0 -> ""
            snippet.candidate.chapterTitle.isNullOrBlank() -> "第${snippet.candidate.chapterIndex + 1}章"
            else -> "第${snippet.candidate.chapterIndex + 1}章·${snippet.candidate.chapterTitle}"
        }
        val node = snippet.candidate.titleHint?.let { "「$it」" }.orEmpty()
        val origin = listOf(book, chapter, node).filter { it.isNotEmpty() }.joinToString("")
        return if (origin.isEmpty()) "[${snippet.index}]（$typeLabel）"
        else "[${snippet.index}]（$typeLabel｜$origin）"
    }

    internal fun stripMarkers(text: String): String =
        historyCitation.replace(text, "").take(MAX_TURN_CHARS)
}
