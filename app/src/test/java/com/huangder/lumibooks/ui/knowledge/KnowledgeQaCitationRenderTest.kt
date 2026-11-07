package com.huangder.lumibooks.ui.knowledge

import androidx.compose.ui.text.SpanStyle
import com.huangder.lumibooks.ai.qa.CitationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AnnotatedString mapping for citation rendering. The tap gesture handler in
 * KnowledgeQaScreen reuses the offset-hit pattern already verified by
 * WelcomeScreen; here we assert the annotation data it reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KnowledgeQaCitationRenderTest {
    private fun parsed() = CitationParser.parse("书中提到「得意而忘言」[1]。〔推断〕两句同源。", sourceCount = 1)

    @Test
    fun answerTextCarriesCitationAnnotationWithNumber() {
        val text = KnowledgeQaCitationUi.buildAnswerText(parsed(), SpanStyle())
        val annotations = text.getStringAnnotations(KnowledgeQaCitationUi.CITATION_TAG, 0, text.length)
        assertEquals(1, annotations.size)
        assertEquals("1", annotations.first().item)
        // The marker itself is rendered as [1].
        assertTrue(text.text.contains("[1]"))
    }

    @Test
    fun outOfRangeMarkersStayLiteralWithoutAnnotation() {
        val text = KnowledgeQaCitationUi.buildAnswerText(
            CitationParser.parse("结论[9]。", sourceCount = 1), SpanStyle()
        )
        assertEquals(
            0,
            text.getStringAnnotations(KnowledgeQaCitationUi.CITATION_TAG, 0, text.length).size
        )
        assertTrue(text.text.contains("[9]"))
    }

    @Test
    fun inferenceLinesAreSeparatedFromBody() {
        assertEquals(listOf("两句同源。"), KnowledgeQaCitationUi.inferenceLines(parsed()))
    }

    @Test
    fun hasBodyTextDistinguishesTextFromMarkersOnly() {
        assertTrue(KnowledgeQaCitationUi.hasBodyText(parsed()))
        assertFalse(
            KnowledgeQaCitationUi.hasBodyText(
                CitationParser.parse("", sourceCount = 3)
            )
        )
    }
}
