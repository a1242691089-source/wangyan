package com.huangder.lumibooks.domain.model

data class Note(
    val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val startPosition: Int,
    val endPosition: Int,
    val startLocatorJson: String? = null,
    val endLocatorJson: String? = null,
    val selectedText: String,
    val note: String,
    val color: String,
    val createdAt: Long,
    val type: String = "highlight",
    val syncId: String = "",
    val updatedAt: Long = createdAt,
    val isNote: Boolean = note.isNotBlank() || type == "note",
    val origin: String = ORIGIN_MANUAL,
    val sourceRuleId: String? = null,
    val sourceMatchKey: String? = null,
    val styleSnapshotJson: String? = null,
    val tags: List<String> = emptyList(),
    val isStale: Boolean = false
) {
    /** The annotation style (highlight/underline) is independent of note intent. */
    val isNoteEntry: Boolean get() = isNote || note.isNotBlank() || type == "note"

    val isGeneratedByHighlightRule: Boolean
        get() = origin == ORIGIN_HIGHLIGHT_RULE && sourceRuleId != null && sourceMatchKey != null

    /** PDF/comic ink strokes share the notes table but are drawings, not text marks. */
    val isInkStroke: Boolean
        get() = type.startsWith(PDF_INK_TYPE_PREFIX) || type.startsWith(EPUB_COMIC_INK_TYPE_PREFIX)

    /**
     * Six-type intent derived from the stored [type] string; `null` when the row
     * is not one of the six intent types (plain style marks, ink strokes, or any
     * unknown future value — those render without a badge for forward compatibility).
     *
     * Legacy collapses (no data migration, storage value is never rewritten):
     * - `"note"` → [NoteType.COMMENT]
     * - underline/highlight with comment text → [NoteType.COMMENT] (modern note
     *   creation stores "highlight" + text, see ReaderScreen note input flow)
     * - highlight without text → [NoteType.EXCERPT]
     */
    val noteType: NoteType?
        get() = when {
            isInkStroke -> null
            type == "underline" && !isNoteEntry -> null
            type == "note" || type == "underline" -> NoteType.COMMENT
            type == "highlight" -> if (isNoteEntry) NoteType.COMMENT else NoteType.EXCERPT
            else -> NoteType.fromValue(type)
        }

    fun detachFromHighlightRule(): Note = copy(
        origin = ORIGIN_MANUAL,
        sourceRuleId = null,
        sourceMatchKey = null
    )

    companion object {
        const val ORIGIN_MANUAL = "manual"
        const val ORIGIN_HIGHLIGHT_RULE = "highlight_rule"
        const val PDF_INK_TYPE_PREFIX = "pdf_ink_"
        const val EPUB_COMIC_INK_TYPE_PREFIX = "epub_comic_ink_"
    }
}
