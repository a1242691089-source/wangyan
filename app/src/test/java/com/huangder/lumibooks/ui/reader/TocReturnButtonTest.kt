package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 自真机迁移（原 TocReturnButtonInstrumentedTest）：规避 Android 16 真机
// androidx.test EmptyActivity 窗口舞步的随机楔死竞态。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w369dp-h822dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TocReturnButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalThemeButtonHasDescriptionAndClicks() = verifyTheme("lumi")

    @Test
    fun liquidGlassButtonHasDescriptionAndClicks() = verifyTheme("liquid_glass")

    private fun verifyTheme(theme: String) {
        var clicks = 0
        val description = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.reader_toc_return_to_current)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppTheme provides theme,
                LocalLiquidGlassCapability provides LiquidGlassCapability(
                    supported = true,
                    hdrSupported = false
                )
            ) {
                TocReturnToCurrentButton(
                    visible = true,
                    motionEnabled = false,
                    onClick = { clicks++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription(description).performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
