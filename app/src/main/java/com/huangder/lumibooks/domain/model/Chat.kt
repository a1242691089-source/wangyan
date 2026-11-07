package com.huangder.lumibooks.domain.model

/** One conversation in the knowledge-QA history list. */
data class ChatSession(
    val id: Long = 0,
    val title: String = "",
    val entryBookId: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val lastMessageAt: Long = createdAt
)

enum class ChatRole(val stored: String) {
    USER("user"),
    ASSISTANT("assistant");

    companion object {
        fun fromStored(value: String?): ChatRole =
            entries.firstOrNull { it.stored == value } ?: ASSISTANT
    }
}

enum class ChatMessageStatus(val stored: String) {
    PENDING("pending"),
    COMPLETE("complete"),
    FAILED("failed");

    companion object {
        fun fromStored(value: String?): ChatMessageStatus =
            entries.firstOrNull { it.stored == value } ?: COMPLETE
    }
}

/** Where a citation points; the label wording per type lives in string resources. */
enum class QaSourceType {
    /** Verbatim book text window. */
    BOOK_BODY,
    /** A note the user wrote (selectedText + note body merged at retrieval). */
    NOTE,
    /** A knowledge evidence excerpt. */
    KNOWLEDGE,
    /** A knowledge node's own thought text. */
    NODE_THOUGHT
}

/**
 * A persisted citation. [n] in the answer text refers to this number
 * (1-based, per-question). chapterIndex/startPosition of -1 mean the source
 * has no anchor to jump to.
 */
data class Citation(
    val n: Int,
    val type: QaSourceType,
    val bookId: String? = null,
    val bookTitle: String? = null,
    val chapterIndex: Int = -1,
    val chapterTitle: String? = null,
    val startPosition: Int = -1,
    val endPosition: Int = -1,
    val quote: String = "",
    /** Set when the source is a knowledge node; null otherwise. */
    val nodeId: Long? = null
) {
    /** null = nothing to jump to (unanchored source or missing book). */
    val jumpTarget: Pair<Int, Int>?
        get() = if (chapterIndex >= 0 && startPosition >= 0) chapterIndex to startPosition else null
}

data class ChatMessage(
    val id: Long = 0,
    val sessionId: Long = 0,
    val role: ChatRole,
    val content: String = "",
    val citations: List<Citation> = emptyList(),
    /** Snippets handed to the model this turn; 0 means retrieval found nothing. */
    val sourceCount: Int = 0,
    val status: ChatMessageStatus,
    val errorCode: String? = null,
    val createdAt: Long
)
