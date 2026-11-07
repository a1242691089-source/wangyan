package com.huangder.lumibooks.ui.welcome

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 自真机迁移（原 WelcomeScreenInstrumentedTest）：SM-S9280 / Android 16 上
// androidx.test 的 EmptyActivity 窗口舞步存在随机楔死竞态（正常 0.2s，楔死时每步
// 操作卡 ~55s 或永不恢复），结构性断言在 Robolectric 上等价且可进 CI。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w369dp-h822dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun introductionUsesUnifiedVersionTwoDesign() {
        showWelcome(isNewInstallation = false, startOnIntroduction = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_brand_name)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_major_version)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_update_title)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.welcome_exit)).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.welcome_app_icon_description))
            .assertDoesNotExist()
    }

    @Test
    fun startButtonKeepsExistingInstallSpecificNextStep() {
        showWelcome(isNewInstallation = true, startOnIntroduction = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.welcome_liquid_glass_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_next)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_excerpt_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_previous)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_liquid_glass_title)).assertIsDisplayed()
    }

    @Test
    fun updateWalkthroughIncludesAllFeaturesAndBackNeverShowsThemeSetup() {
        showWelcome(isNewInstallation = false, startOnIntroduction = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_excerpt_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_enable_liquid_glass)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.welcome_previous)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_next)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_g2_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_next)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_dictionary_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_previous)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_g2_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_next)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_next)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.welcome_support_title)).assertIsDisplayed()
    }

    @Test
    fun supportPageContainsCopyAndActionsWithoutDecorativeEmoji() {
        showWelcome(isNewInstallation = false, startOnSupport = true)

        composeRule.onNodeWithText(context.getString(R.string.welcome_support_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_open_sponsor)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_start_using)).assertIsDisplayed()
        composeRule.onNodeWithText("☕").assertDoesNotExist()
        composeRule.onNodeWithText("✨").assertDoesNotExist()
        composeRule.onNodeWithText("💗").assertDoesNotExist()
    }

    private fun showWelcome(
        isNewInstallation: Boolean,
        startOnIntroduction: Boolean = false,
        startOnSupport: Boolean = false
    ) {
        composeRule.setContent {
            EBookReaderTheme(
                darkTheme = false,
                eInkMode = false,
                // 静态断言页不依赖动画：REDUCED 让渐变背景走静态分支，
                // 避免 3 路 infiniteRepeatable 在无帧推进环境下的不确定性
                motionPreference = MotionPreference.REDUCED
            ) {
                WelcomeScreen(
                    isNewInstallation = isNewInstallation,
                    shouldShowLanguageSetup = false,
                    initialLanguage = "zh-CN",
                    initialEInkMode = false,
                    isEInkMode = false,
                    isDark = false,
                    isLiquidGlass = false,
                    onFinished = {},
                    onOpenSponsor = {},
                    onLanguageSetupComplete = { _, _ -> },
                    onEnableLiquidGlass = {},
                    startOnIntroduction = startOnIntroduction,
                    startOnSupport = startOnSupport
                )
            }
        }
    }
}
