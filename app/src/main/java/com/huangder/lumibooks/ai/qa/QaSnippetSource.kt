package com.huangder.lumibooks.ai.qa

/**
 * Retrieval seam between the QA engine and the data layer, so engine-level
 * tests can substitute a canned snippet source.
 */
interface QaSnippetSource {
    suspend fun load(entryBookId: String?, query: RetrievalQuery): List<SourceSnippet>
}
