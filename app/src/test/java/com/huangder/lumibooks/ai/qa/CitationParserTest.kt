package com.huangder.lumibooks.ai.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitationParserTest {
    @Test
    fun splitsPlainTextAndCitations() {
        val parsed = CitationParser.parse("得意而忘言[1]。得鱼忘筌[2]。", sourceCount = 2)
        assertEquals(
            listOf(
                CitationParser.AnswerSegment.Plain("得意而忘言"),
                CitationParser.AnswerSegment.Cite(1),
                CitationParser.AnswerSegment.Plain("。得鱼忘筌"),
                CitationParser.AnswerSegment.Cite(2),
                CitationParser.AnswerSegment.Plain("。")
            ),
            parsed.segments
        )
        assertEquals(listOf(1, 2), parsed.citedIndices)
        assertTrue(parsed.unresolved.isEmpty())
    }

    @Test
    fun outOfRangeNumbersStayLiteralAndAreReported() {
        val parsed = CitationParser.parse("结论[9]。", sourceCount = 2)
        assertTrue(parsed.segments.all { it is CitationParser.AnswerSegment.Plain })
        assertTrue(parsed.segments.any { (it as CitationParser.AnswerSegment.Plain).text.contains("[9]") })
        assertEquals(listOf(9), parsed.unresolved)
        assertTrue(parsed.citedIndices.isEmpty())
    }

    @Test
    fun inferenceMarkerMarksTheWholeSentence() {
        val parsed = CitationParser.parse("书中写道[1]。〔推断〕这两段可能同源。", sourceCount = 1)
        val inference = parsed.segments.filterIsInstance<CitationParser.AnswerSegment.Inference>()
        assertEquals(1, inference.size)
        assertEquals("这两段可能同源。", inference.single().text)
    }

    @Test
    fun alternateInferenceBracketsAreRecognized() {
        listOf("［推断］", "[推断]", "（推断）", "(推断)").forEach { marker ->
            val parsed = CitationParser.parse("${marker}猜测内容。", sourceCount = 0)
            assertEquals(
                "marker $marker should mark inference",
                1,
                parsed.segments.count { it is CitationParser.AnswerSegment.Inference }
            )
        }
    }

    @Test
    fun adjacentCitationsParseBackToBack() {
        val parsed = CitationParser.parse("两个来源[1][2]都支持。", sourceCount = 2)
        assertEquals(
            listOf(
                CitationParser.AnswerSegment.Plain("两个来源"),
                CitationParser.AnswerSegment.Cite(1),
                CitationParser.AnswerSegment.Cite(2),
                CitationParser.AnswerSegment.Plain("都支持。")
            ),
            parsed.segments
        )
    }

    @Test
    fun quotedBracketsInsideCitationStyleStillParse() {
        // A bracket inside a quote is still a marker if it matches \[\d{1,2}]; the
        // protocol requires the model to quote verbatim WITHOUT markers, so any
        // well-formed bracket is treated as one.
        val parsed = CitationParser.parse("他说「参见[1]」[2]。", sourceCount = 2)
        assertEquals(2, parsed.citedIndices.size)
    }

    @Test
    fun emptyAndBlankContentParseToEmpty() {
        assertEquals(0, CitationParser.parse("", sourceCount = 3).segments.size)
        assertEquals(0, CitationParser.parse("   \n ", sourceCount = 3).segments.size)
    }

    @Test
    fun pureInferenceAnswerHasNoCitations() {
        val parsed = CitationParser.parse("〔推断〕没有任何片段支持这一点。", sourceCount = 0)
        assertEquals(1, parsed.segments.size)
        assertTrue(parsed.segments.single() is CitationParser.AnswerSegment.Inference)
    }
}
