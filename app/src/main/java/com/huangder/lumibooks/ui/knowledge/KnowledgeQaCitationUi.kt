package com.huangder.lumibooks.ui.knowledge

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import com.huangder.lumibooks.ai.qa.CitationParser

/**
 * Pure(ish) mapping from a parsed answer to renderable pieces, kept out of
 * composables so Robolectric tests can assert on it without a full screen.
 */
internal object KnowledgeQaCitationUi {
    const val CITATION_TAG = "qa_cite"

    /** Main body: plain text plus superscript citation markers carrying the number. */
    fun buildAnswerText(parsed: CitationParser.ParsedAnswer, markerStyle: SpanStyle): AnnotatedString =
        buildAnnotatedString {
            parsed.segments.forEach { segment ->
                when (segment) {
                    is CitationParser.AnswerSegment.Plain -> append(segment.text)
                    is CitationParser.AnswerSegment.Cite -> withAnnotation(CITATION_TAG, segment.n.toString()) {
                        withStyle(markerStyle) { append("[${segment.n}]") }
                    }
                    is CitationParser.AnswerSegment.Inference -> Unit // rendered separately
                }
            }
        }

    /** Inference sentences, in order, for the visually separated block. */
    fun inferenceLines(parsed: CitationParser.ParsedAnswer): List<String> =
        parsed.segments.mapNotNull { segment ->
            (segment as? CitationParser.AnswerSegment.Inference)?.text
        }

    /** Whether any body text (non-marker) exists at all. */
    fun hasBodyText(parsed: CitationParser.ParsedAnswer): Boolean =
        parsed.segments.any {
            it is CitationParser.AnswerSegment.Plain && it.text.isNotBlank()
        }
}
