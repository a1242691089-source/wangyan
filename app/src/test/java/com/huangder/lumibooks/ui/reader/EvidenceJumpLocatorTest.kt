package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvidenceJumpLocatorTest {
    @Test
    fun locatorCarriesChapterAndCharacterOffset() {
        val locator = evidenceJumpLocator(
            targetChapter = 3, targetPosition = 128, chapterCount = 10, bookLayoutRender = false
        )!!
        assertEquals(3, locator.chapterIndex)
        assertEquals(128, locator.characterOffset)
        assertEquals(0f, locator.chapterFraction)
    }

    @Test
    fun bookLayoutDropsCharacterOffsetButKeepsChapter() {
        val locator = evidenceJumpLocator(
            targetChapter = 2, targetPosition = 64, chapterCount = 5, bookLayoutRender = true
        )!!
        assertEquals(2, locator.chapterIndex)
        assertNull(locator.characterOffset)
    }

    @Test
    fun outOfRangeChapterReturnsNull() {
        assertNull(
            evidenceJumpLocator(targetChapter = 5, targetPosition = 0, chapterCount = 5, bookLayoutRender = false)
        )
        assertNull(
            evidenceJumpLocator(targetChapter = 9, targetPosition = 0, chapterCount = 0, bookLayoutRender = false)
        )
    }

    @Test
    fun negativeTargetsReturnNull() {
        assertNull(
            evidenceJumpLocator(targetChapter = -1, targetPosition = 40, chapterCount = 5, bookLayoutRender = false)
        )
        assertNull(
            evidenceJumpLocator(targetChapter = 0, targetPosition = -1, chapterCount = 5, bookLayoutRender = false)
        )
    }
}
