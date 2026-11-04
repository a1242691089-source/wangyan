package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 原生渲染判定原本是 Composable 里一行 12 个条件的布尔表达式 + 11 条手写日志 if，
 * 无法单测。收进 [EpubRenderConfig.nativeSettingsBlockers] 后在这里逐条钉住，
 * 断言的是「精确原因」，不是「不支持」——条件写重/漏写时能立刻看出是哪一条。
 */
class EpubRenderConfigTest {

    private fun config() = EpubRenderConfig(
        fontSizeSp = 18f,
        fontType = "system",
        fontFilePath = null,
        textColorOverride = null,
        theme = "day",
        coverBackgroundColor = null,
        nativeCssEnabled = true
    )

    private fun blockers(
        config: EpubRenderConfig,
        hasNativeSession: Boolean = true,
        bookFormat: String = "EPUB",
        mediaOnlyPage: Boolean? = false
    ): List<String> = config.nativeSettingsBlockers(hasNativeSession, bookFormat, mediaOnlyPage)

    @Test
    fun baselineSupportsNativeSettings() {
        assertTrue(blockers(config()).isEmpty())
    }

    @Test
    fun experimentSwitchOffBlocks() {
        assertEquals(listOf("experiment disabled"), blockers(config().copy(nativeCssEnabled = false)))
    }

    @Test
    fun missingNativeSessionBlocks() {
        assertEquals(listOf("format EPUB"), blockers(config(), hasNativeSession = false))
    }

    @Test
    fun nonEpubFormatBlocksCaseSensitively() {
        assertEquals(listOf("format PDF"), blockers(config(), bookFormat = "PDF"))
        // 既有行为是严格 == "EPUB"，小写不应被当成支持。
        assertEquals(listOf("format epub"), blockers(config(), bookFormat = "epub"))
    }

    @Test
    fun mediaOnlyPageUsesThreeStateCheck() {
        // 首帧 produceState 还没回来（null）时同样不能走原生引擎。
        assertEquals(listOf("media-only page"), blockers(config(), mediaOnlyPage = null))
        assertEquals(listOf("media-only page"), blockers(config(), mediaOnlyPage = true))
        assertTrue(blockers(config(), mediaOnlyPage = false).isEmpty())
    }

    @Test
    fun continuousScrollBlocks() {
        assertEquals(listOf("continuous scroll"), blockers(config().copy(continuousScroll = true)))
    }

    @Test
    fun onlySlideCurlAndNoneTransitionsAreSupported() {
        for (transition in listOf("slide", "curl", "none")) {
            assertTrue(transition, blockers(config().copy(pageTransition = transition)).isEmpty())
        }
        for (transition in listOf("scroll", "cover", "fade", "")) {
            assertEquals(listOf("transition $transition"), blockers(config().copy(pageTransition = transition)))
        }
    }

    @Test
    fun readerBackgroundImageBlocks() {
        assertEquals(
            listOf("background image"),
            blockers(config().copy(readerBackgroundImagePath = "/data/background.webp"))
        )
    }

    @Test
    fun bionicReadingBlocks() {
        assertEquals(listOf("bionic reading"), blockers(config().copy(bionicReadingEnabled = true)))
    }

    @Test
    fun chineseConversionBlocks() {
        assertEquals(
            listOf("Chinese conversion traditional"),
            blockers(config().copy(chineseMode = "traditional"))
        )
    }

    @Test
    fun nonNaturalAlignmentBlocks() {
        // 日志沿用原实现：直接插值枚举，得到大写名字而不是 key。
        assertEquals(listOf("alignment LEFT"), blockers(config().copy(textAlignment = ReaderTextAlignment.LEFT)))
    }

    @Test
    fun letterSpacingMustBeExactlyZero() {
        assertTrue(blockers(config().copy(letterSpacingDp = 0f)).isEmpty())
        assertEquals(listOf("letter spacing"), blockers(config().copy(letterSpacingDp = 0.01f)))
    }

    @Test
    fun imageCropBlocks() {
        assertEquals(listOf("image crop"), blockers(config().copy(imagePageCrop = true)))
    }

    @Test
    fun imageAdjustmentsBlock() {
        // 用 brightness 而不是 sharpen：forDisplay(eInk) 会把 sharpen 归零，容易写成假阳性。
        assertEquals(
            listOf("image adjustments"),
            blockers(config().copy(imageAdjustments = ReaderImageAdjustments(brightness = 0.1f)))
        )
        assertTrue(
            blockers(config().copy(imageAdjustments = ReaderImageAdjustments())).isEmpty()
        )
    }

    @Test
    fun multipleBlockersKeepLogOrder() {
        assertEquals(
            listOf("continuous scroll", "bionic reading", "image crop"),
            blockers(config().copy(continuousScroll = true, bionicReadingEnabled = true, imagePageCrop = true))
        )
    }
}
