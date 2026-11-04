package com.huangder.lumibooks.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.NoteType

/** Shared presentation helpers for the six note intent types. */

@StringRes
fun NoteType.labelRes(): Int = when (this) {
    NoteType.EXCERPT -> R.string.note_type_excerpt
    NoteType.COMMENT -> R.string.note_type_comment
    NoteType.THOUGHT -> R.string.note_type_thought
    NoteType.QUESTION -> R.string.note_type_question
    NoteType.INSPIRATION -> R.string.note_type_inspiration
    NoteType.AI_SUMMARY -> R.string.note_type_ai_summary
}

/** Badge accent per type; AI summary additionally renders an "AI" source prefix. */
fun NoteType.accentColor(): Color = when (this) {
    NoteType.EXCERPT -> Color(0xFFE9C46A)
    NoteType.COMMENT -> Color(0xFF6C8EBF)
    NoteType.THOUGHT -> Color(0xFF81B29A)
    NoteType.QUESTION -> Color(0xFFE76F51)
    NoteType.INSPIRATION -> Color(0xFF9B5DE5)
    NoteType.AI_SUMMARY -> Color(0xFF8D99AE)
}
