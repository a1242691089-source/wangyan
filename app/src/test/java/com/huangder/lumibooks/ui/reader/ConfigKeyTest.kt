package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.LongPressSelectionRange
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * [configKey] 是翻页 / 重排 / preload 的唯一开关。它错了不会崩，只会表现为翻页异常或
 * preload 命中率下降，所以在这里把输出契约钉死，作为「三张参数表统一」前的安全网。
 *
 * 五组用例各挡一类错误：
 * - A 精确串：顺序、分隔符、兜底值、任意 `toString` 变化
 * - B 逐字段改值：新增字段忘了写进 `listOf`（重构时最容易犯）
 * - C null 兜底：P3b 把 `orEmpty()` / `?: -1` 改掉会让全员首屏重排
 * - D 请求 token：漏进键会复用旧页面
 * - E 动效档位：被踢出缓存维度会画出错误的翻页动画
 *
 * 纯 JUnit，无 Robolectric——依赖的 `EpubTextSpacing`、三个枚举、`Pair` 都不碰 Android。
 */
class ConfigKeyTest {

    /**
     * 改这个字符串 = 改所有用户的首屏缓存键，等于全员重排一次。
     * 要改必须在 commit message 里单独说明，不能夹在重构里顺手带过。
     */
    private val goldenKey =
        "sig-main|(1080, 1920)|3|18.0|0.0|" +
            "EpubTextSpacing(lineHeight=1.5, paragraphSpacingDp=2.0, firstLineIndent=2.0)|" +
            "system||400|-1|day|-1|-1|-1|false||natural|true|" +
            "false|false|false|false|original|false|" +
            "slide|260|1.0|left_previous_right_next|word|24.0|" +
            "20.0|24.0|20.0||-1|-1"

    @Before
    fun pinMotionPreference() {
        // durationScale 是 @Volatile 全局状态，只能经 setPreference 改。
        // 不能假设别的测试类恢复过——FastPageTimingTest / BookReaderHandoffTest 会把它设成 FAST。
        VisualMotion.setPreference(MotionPreference.STANDARD)
    }

    @After
    fun resetMotionPreference() {
        VisualMotion.setPreference(MotionPreference.STANDARD)
    }

    @Suppress("LongParameterList")
    private fun key(
        paginationSignature: String = "sig-main",
        viewportSize: Pair<Int, Int> = 1080 to 1920,
        chapterIndex: Int = 3,
        fontSizeSp: Float = 18f,
        letterSpacingDp: Float = 0f,
        textSpacing: EpubTextSpacing = EpubTextSpacing(),
        fontType: String = "system",
        fontFilePath: String? = null,
        bodyFontWeight: Int = 400,
        textColorOverride: Int? = null,
        theme: String = "day",
        coverBackgroundColor: Int? = null,
        readerBackgroundColor: Int? = null,
        autoTextColor: Int? = null,
        readerBackgroundHasImage: Boolean = false,
        readerBackgroundUrl: String? = null,
        textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
        preservePublisherBackground: Boolean = true,
        imagePageCrop: Boolean = false,
        publisherPaintOnly: Boolean = false,
        restoreProgressionInclusive: Boolean = false,
        bionicReadingEnabled: Boolean = false,
        chineseMode: String = "original",
        continuousScroll: Boolean = false,
        pageTransition: String = "slide",
        pageTransitionDurationMs: Int = 260,
        edgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
        longPressSelectionRange: LongPressSelectionRange = LongPressSelectionRange.DEFAULT,
        marginTopDp: Float = 24f,
        marginRightDp: Float = 20f,
        marginBottomDp: Float = 24f,
        marginLeftDp: Float = 20f,
        initialFragment: String? = null,
        locatorRequest: EpubLocatorRequest? = null,
        pageRequest: EpubPageRequest? = null
    ): String = configKey(
        paginationSignature = paginationSignature,
        viewportSize = viewportSize,
        chapterIndex = chapterIndex,
        fontSizeSp = fontSizeSp,
        letterSpacingDp = letterSpacingDp,
        textSpacing = textSpacing,
        fontType = fontType,
        fontFilePath = fontFilePath,
        bodyFontWeight = bodyFontWeight,
        textColorOverride = textColorOverride,
        theme = theme,
        coverBackgroundColor = coverBackgroundColor,
        readerBackgroundColor = readerBackgroundColor,
        autoTextColor = autoTextColor,
        readerBackgroundHasImage = readerBackgroundHasImage,
        readerBackgroundUrl = readerBackgroundUrl,
        textAlignment = textAlignment,
        preservePublisherBackground = preservePublisherBackground,
        imagePageCrop = imagePageCrop,
        publisherPaintOnly = publisherPaintOnly,
        restoreProgressionInclusive = restoreProgressionInclusive,
        bionicReadingEnabled = bionicReadingEnabled,
        chineseMode = chineseMode,
        continuousScroll = continuousScroll,
        pageTransition = pageTransition,
        pageTransitionDurationMs = pageTransitionDurationMs,
        edgeTapMode = edgeTapMode,
        longPressSelectionRange = longPressSelectionRange,
        marginTopDp = marginTopDp,
        marginRightDp = marginRightDp,
        marginBottomDp = marginBottomDp,
        marginLeftDp = marginLeftDp,
        initialFragment = initialFragment,
        locatorRequest = locatorRequest,
        pageRequest = pageRequest
    )

    // ─── A. 精确串 ───

    @Test
    fun goldenKeyIsStable() {
        assertEquals(goldenKey, key())
    }

    // ─── B. 每个字段都必须参与键 ───

    @Test
    fun everyFieldChangesTheKey() {
        val baseline = key()
        val cases: List<Pair<String, () -> String>> = listOf(
            "paginationSignature" to { key(paginationSignature = "sig-other") },
            "viewportSize" to { key(viewportSize = 1080 to 1921) },
            "chapterIndex" to { key(chapterIndex = 4) },
            "fontSizeSp" to { key(fontSizeSp = 19f) },
            "letterSpacingDp" to { key(letterSpacingDp = 0.5f) },
            "textSpacing" to { key(textSpacing = EpubTextSpacing(lineHeight = 1.8f)) },
            "fontType" to { key(fontType = "serif") },
            "fontFilePath" to { key(fontFilePath = "/data/fonts/custom.ttf") },
            "bodyFontWeight" to { key(bodyFontWeight = 500) },
            "textColorOverride" to { key(textColorOverride = 0xFF112233.toInt()) },
            "theme" to { key(theme = "night") },
            "coverBackgroundColor" to { key(coverBackgroundColor = 0xFF102030.toInt()) },
            "readerBackgroundColor" to { key(readerBackgroundColor = 0xFF203040.toInt()) },
            "autoTextColor" to { key(autoTextColor = 0xFF405060.toInt()) },
            "readerBackgroundHasImage" to { key(readerBackgroundHasImage = true) },
            "readerBackgroundUrl" to { key(readerBackgroundUrl = "/data/background.webp") },
            "textAlignment" to { key(textAlignment = ReaderTextAlignment.LEFT) },
            "preservePublisherBackground" to { key(preservePublisherBackground = false) },
            "imagePageCrop" to { key(imagePageCrop = true) },
            "publisherPaintOnly" to { key(publisherPaintOnly = true) },
            "restoreProgressionInclusive" to { key(restoreProgressionInclusive = true) },
            "bionicReadingEnabled" to { key(bionicReadingEnabled = true) },
            "chineseMode" to { key(chineseMode = "traditional") },
            "continuousScroll" to { key(continuousScroll = true) },
            "pageTransition" to { key(pageTransition = "curl") },
            "pageTransitionDurationMs" to { key(pageTransitionDurationMs = 300) },
            "edgeTapMode" to { key(edgeTapMode = ReaderEdgeTapMode.BOTH_NEXT) },
            "longPressSelectionRange" to {
                key(longPressSelectionRange = LongPressSelectionRange.PARAGRAPH)
            },
            "marginTopDp" to { key(marginTopDp = 25f) },
            "marginRightDp" to { key(marginRightDp = 21f) },
            "marginBottomDp" to { key(marginBottomDp = 25f) },
            "marginLeftDp" to { key(marginLeftDp = 21f) },
            "initialFragment" to { key(initialFragment = "epubcfi(/6/4!4/2/2)") },
            "locatorRequest" to { key(locatorRequest = EpubLocatorRequest(7, 3, "{}")) },
            "pageRequest" to { key(pageRequest = EpubPageRequest(9, 3, 0)) }
        )
        // 35 个参数逐一核对：漏一个就说明它没进 listOf，页面上改了却不重排。
        assertEquals(35, cases.size)
        for ((name, build) in cases) {
            assertNotEquals("字段 $name 未参与缓存键", baseline, build())
        }
    }

    // ─── C. null 与哨兵值同键（既有语义，记录不修）───

    @Test
    fun nullAndSentinelCollapseIntoSameKey() {
        // orEmpty()：null 与空串不可区分。
        assertEquals(key(fontFilePath = null), key(fontFilePath = ""))
        assertEquals(key(readerBackgroundUrl = null), key(readerBackgroundUrl = ""))
        assertEquals(key(initialFragment = null), key(initialFragment = ""))

        // ?: -1：null 与 ARGB -1（纯白 0xFFFFFFFF）不可区分——这是既有缺陷，
        // P3b 重构时必须原样保留，改动属于行为变更。
        assertEquals(key(textColorOverride = null), key(textColorOverride = -1))
        assertEquals(key(coverBackgroundColor = null), key(coverBackgroundColor = -1))
        assertEquals(key(readerBackgroundColor = null), key(readerBackgroundColor = -1))
        assertEquals(key(autoTextColor = null), key(autoTextColor = -1))

        // 请求对象同理：token = -1 与「没有请求」同键。
        assertEquals(
            key(locatorRequest = null),
            key(locatorRequest = EpubLocatorRequest(-1, 3, "{}"))
        )
        assertEquals(
            key(pageRequest = null),
            key(pageRequest = EpubPageRequest(-1, 3, 0))
        )
    }

    // ─── D. 请求 token 必须区分 ───

    @Test
    fun requestTokensParticipate() {
        assertNotEquals(
            key(locatorRequest = EpubLocatorRequest(1, 3, "{}")),
            key(locatorRequest = EpubLocatorRequest(2, 3, "{}"))
        )
        assertNotEquals(
            key(pageRequest = EpubPageRequest(1, 3, 0)),
            key(pageRequest = EpubPageRequest(2, 3, 0))
        )
        // 章节/页码不参与——请求对象只按 token 区分，这是既有行为。
        assertEquals(
            key(locatorRequest = EpubLocatorRequest(1, 3, "{}")),
            key(locatorRequest = EpubLocatorRequest(1, 4, "{\"other\":true}"))
        )
    }

    // ─── E. 动效档位参与键 ───

    @Test
    fun durationScaleParticipates() {
        val standard = key()
        VisualMotion.setPreference(MotionPreference.FAST)
        val fast = key()
        assertNotEquals("durationScale 未参与缓存键", standard, fast)
    }
}
