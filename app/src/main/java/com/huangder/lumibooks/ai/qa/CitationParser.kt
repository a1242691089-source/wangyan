package com.huangder.lumibooks.ai.qa

/**
 * Parses a model answer into renderable segments: plain text, citation
 * markers [n], and whole sentences the model flagged as inference (〔推断〕).
 * Out-of-range citation numbers are kept as literal text — silently dropping
 * them would hide that the model broke the protocol.
 */
object CitationParser {
    sealed interface AnswerSegment {
        data class Plain(val text: String) : AnswerSegment
        data class Cite(val n: Int) : AnswerSegment
        data class Inference(val text: String) : AnswerSegment
    }

    data class ParsedAnswer(
        val segments: List<AnswerSegment>,
        /** Citation numbers the model actually used, deduplicated and ascending. */
        val citedIndices: List<Int>,
        /** Numbers outside 1..sourceCount, rendered back as literal text. */
        val unresolved: List<Int>
    )

    private val citationRegex = Regex("\\[(\\d{1,2})]")

    // Sentence terminators keep their punctuation; newline also breaks.
    private val sentenceBreak = Regex("(?<=[。！？!?；;\\n])")

    /** Marker variants the model may produce despite the prompt's exact form. */
    private val inferenceMarkers = listOf("〔推断〕", "［推断］", "[推断]", "（推断）", "(推断)")

    fun parse(content: String, sourceCount: Int): ParsedAnswer {
        if (content.isBlank()) {
            return ParsedAnswer(emptyList(), emptyList(), emptyList())
        }
        val segments = ArrayList<AnswerSegment>()
        val cited = LinkedHashSet<Int>()
        val unresolved = LinkedHashSet<Int>()

        content.split(sentenceBreak).forEach { rawSentence ->
            val sentence = rawSentence.trim()
            if (sentence.isEmpty()) return@forEach
            val inference = matchInferenceMarker(sentence)
            if (inference != null) {
                segments.add(AnswerSegment.Inference(inference))
                return@forEach
            }
            var lastIndex = 0
            citationRegex.findAll(sentence).forEach { match ->
                val number = match.groupValues[1].toInt()
                val before = sentence.substring(lastIndex, match.range.first)
                if (before.isNotEmpty()) segments.add(AnswerSegment.Plain(before))
                if (number in 1..sourceCount) {
                    segments.add(AnswerSegment.Cite(number))
                    cited.add(number)
                } else {
                    segments.add(AnswerSegment.Plain(match.value))
                    unresolved.add(number)
                }
                lastIndex = match.range.last + 1
            }
            val tail = sentence.substring(lastIndex)
            if (tail.isNotEmpty()) segments.add(AnswerSegment.Plain(tail))
        }
        // Merge adjacent plain runs split only by citation parsing boundaries.
        val merged = ArrayList<AnswerSegment>(segments.size)
        segments.forEach { segment ->
            val previous = merged.lastOrNull()
            if (segment is AnswerSegment.Plain && previous is AnswerSegment.Plain) {
                merged[merged.size - 1] = AnswerSegment.Plain(previous.text + segment.text)
            } else {
                merged.add(segment)
            }
        }
        return ParsedAnswer(merged, cited.toList().sorted(), unresolved.toList().sorted())
    }

    private fun matchInferenceMarker(sentence: String): String? {
        val marker = inferenceMarkers.firstOrNull { sentence.startsWith(it) } ?: return null
        val body = sentence.removePrefix(marker).trim()
        return body.ifEmpty { null }
    }
}
