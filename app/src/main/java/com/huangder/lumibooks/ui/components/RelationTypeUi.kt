package com.huangder.lumibooks.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.KnowledgeRelationType

/** Shared presentation helpers for the nine knowledge relation types. */

@StringRes
fun KnowledgeRelationType.labelRes(): Int = when (this) {
    KnowledgeRelationType.MENTIONS -> R.string.relation_type_mentions
    KnowledgeRelationType.EXPLAINS -> R.string.relation_type_explains
    KnowledgeRelationType.SUPPORTS -> R.string.relation_type_supports
    KnowledgeRelationType.CONTRADICTS -> R.string.relation_type_contradicts
    KnowledgeRelationType.EXTENDS -> R.string.relation_type_extends
    KnowledgeRelationType.BELONGS_TO -> R.string.relation_type_belongs_to
    KnowledgeRelationType.APPLIES_TO -> R.string.relation_type_applies_to
    KnowledgeRelationType.EXEMPLIFIES -> R.string.relation_type_exemplifies
    KnowledgeRelationType.RELATED_TO -> R.string.relation_type_related_to
}

/** Badge accent per relation type, tuned to sit next to the note type palette. */
fun KnowledgeRelationType.accentColor(): Color = when (this) {
    KnowledgeRelationType.MENTIONS -> Color(0xFF6C8EBF)
    KnowledgeRelationType.EXPLAINS -> Color(0xFF81B29A)
    KnowledgeRelationType.SUPPORTS -> Color(0xFF4D908E)
    KnowledgeRelationType.CONTRADICTS -> Color(0xFFE76F51)
    KnowledgeRelationType.EXTENDS -> Color(0xFF577590)
    KnowledgeRelationType.BELONGS_TO -> Color(0xFFB08968)
    KnowledgeRelationType.APPLIES_TO -> Color(0xFFE9C46A)
    KnowledgeRelationType.EXEMPLIFIES -> Color(0xFF9B5DE5)
    KnowledgeRelationType.RELATED_TO -> Color(0xFF8D99AE)
}
