package com.huangder.lumibooks.ai.qa

/**
 * Query tokenization for the retrieval layer. No dictionary dependency:
 * ASCII runs become words, Chinese runs become sliding bigrams (the standard
 * retrieval fallback for unsegmented CJK). Stopwords only remove noise terms
 * that would otherwise dominate BM25's idf.
 */
object QaTokenizer {
    private const val MAX_TERMS = 24

    private val asciiWord = Regex("[a-z0-9]{2,}")
    private val cjkRun = Regex("[\\u4e00-\\u9fff]+")

    /** Interrogative scaffolding that carries no topical signal. */
    private val stopWords = setOf(
        "什么", "如何", "为何", "这本", "本书", "书中", "是否", "请问", "一下",
        "哪些", "怎么", "多少", "可以", "关于", "以及", "还有", "就是",
        "what", "who", "why", "how", "does", "did", "is", "the", "and", "for", "you",
        "book", "this", "that", "with", "are", "was", "can"
    )

    fun query(raw: String): RetrievalQuery {
        val lowered = raw.lowercase()
        val terms = LinkedHashMap<String, QaTerm>()

        fun add(text: String, weight: Float) {
            if (terms.size >= MAX_TERMS) return
            if (text in stopWords) return
            terms.putIfAbsent(text, QaTerm(text, weight))
        }

        asciiWord.findAll(lowered).forEach { add(it.value, 1.1f) }
        cjkRun.findAll(raw).forEach { match ->
            val run = match.value
            if (run.length == 1) {
                add(run, 1.0f)
            } else {
                for (i in 0 until run.length - 1) add(run.substring(i, i + 2), 1.0f)
            }
        }
        return RetrievalQuery(raw = raw, terms = terms.values.toList())
    }

    /** Non-overlapping case-insensitive occurrence count, capped so a repeated
     *  term inside one long document cannot dominate the score. */
    fun countOccurrences(text: String, term: String, cap: Int = 4): Int {
        if (term.isEmpty() || text.isEmpty()) return 0
        val haystack = text.lowercase()
        val needle = term.lowercase()
        var count = 0
        var from = 0
        while (count < cap) {
            val index = haystack.indexOf(needle, from)
            if (index < 0) break
            count++
            from = index + needle.length
        }
        return count
    }
}
