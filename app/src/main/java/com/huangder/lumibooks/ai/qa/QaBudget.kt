package com.huangder.lumibooks.ai.qa

/**
 * Context-window packing. The budget is deliberately conservative: an
 * oversized context surfaces downstream as a 400 (mapped to
 * MODEL_UNAVAILABLE) that reads like a broken configuration.
 */
object QaBudget {
    data class BudgetLimits(
        val totalTokens: Int = 8000,
        val systemPromptTokens: Int = 1600,
        val snippetTokens: Int = 5000,
        val historyTokens: Int = 1200,
        val maxSnippets: Int = 12,
        val maxPerBook: Int = 3,
        val maxPerChapter: Int = 2,
        val snippetMaxChars: Int = 500
    )

    /**
     * Rough token estimate: CJK ≈ 1 token per char, other scripts ≈ 0.28
     * (about 3.5 chars/token). Conservative by design.
     */
    fun estimateTokens(text: String): Int {
        var cjk = 0
        text.forEach { ch ->
            if (ch.code in 0x4e00..0x9fff || ch.code in 0x3400..0x4dbf) cjk++
        }
        val other = text.length - cjk
        return cjk + (other * 28 / 100) + 1
    }

    /**
     * Selects and numbers snippets. Skips (never truncates mid-sentence) items
     * that do not fit the char budget, letting later shorter items fill the
     * remaining space. The returned order is the citation numbering.
     */
    fun pack(ranked: List<ScoredSource>, limits: BudgetLimits = BudgetLimits()): List<SourceSnippet> {
        val picked = ArrayList<SourceSnippet>(limits.maxSnippets)
        var used = 0
        val perBook = HashMap<String?, Int>()
        val perChapter = HashMap<Pair<String?, Int>, Int>()
        for (scored in ranked) {
            if (picked.size >= limits.maxSnippets) break
            if (scored.score <= 0f) continue
            val candidate = scored.candidate
            val book = candidate.bookId
            if (book != null && (perBook[book] ?: 0) >= limits.maxPerBook) continue
            val chapterKey = book to candidate.chapterIndex
            if (book != null && candidate.chapterIndex >= 0 &&
                (perChapter[chapterKey] ?: 0) >= limits.maxPerChapter
            ) continue
            val text = candidate.text.take(limits.snippetMaxChars)
            if (used + text.length > limits.snippetTokens) continue
            picked.add(SourceSnippet(index = picked.size + 1, candidate = candidate.copy(text = text)))
            used += text.length
            if (book != null) perBook.merge(book, 1, Int::plus)
            if (book != null && candidate.chapterIndex >= 0) perChapter.merge(chapterKey, 1, Int::plus)
        }
        return picked
    }

    /**
     * How much prompt history fits. Trims from the oldest side; each turn is
     * capped individually so one long answer cannot evict everything else.
     */
    fun historyLimit(turns: List<QaTurn>, limits: BudgetLimits = BudgetLimits()): List<QaTurn> {
        var used = 0
        val kept = ArrayList<QaTurn>()
        for (turn in turns.reversed()) {
            val cost = estimateTokens(turn.question) + estimateTokens(turn.answer)
            if (used + cost > limits.historyTokens) break
            kept.add(0, turn)
            used += cost
        }
        return kept
    }

    /**
     * Final guard: if system+snippets+history+question exceed the total, drop
     * the last-picked (lowest-scored) snippets. Indices of the survivors stay
     * contiguous 1..n, so no renumbering is needed.
     */
    fun trimToTotal(
        snippets: List<SourceSnippet>,
        systemPrompt: String,
        history: List<QaTurn>,
        question: String,
        limits: BudgetLimits = BudgetLimits()
    ): List<SourceSnippet> {
        var fixed = estimateTokens(systemPrompt) + estimateTokens(question) +
            history.sumOf { estimateTokens(it.question) + estimateTokens(it.answer) }
        val result = snippets.toMutableList()
        while (result.isNotEmpty() &&
            fixed + result.sumOf { estimateTokens(it.candidate.text) } > limits.totalTokens
        ) {
            result.removeAt(result.size - 1)
        }
        return result
    }
}
