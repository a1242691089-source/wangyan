package com.huangder.lumibooks.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.AppIconStyle
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 自真机迁移（原 AppIconStyleScreenInstrumentedTest）：规避 Android 16 真机
// androidx.test EmptyActivity 窗口舞步的随机楔死竞态。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w369dp-h822dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppIconStyleScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun optionsExposeSelectionAndUpdateImmediately() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val lumi2Label = context.getString(R.string.icon_style_lumi2)
        val classicLabel = context.getString(R.string.icon_style_classic)
        var selectedStyle by mutableStateOf(AppIconStyle.LUMI_2.storedValue)

        composeRule.setContent {
            EBookReaderTheme(darkTheme = false) {
                AppIconStyleOptions(
                    selectedStyle = selectedStyle,
                    onSelect = { selectedStyle = it }
                )
            }
        }

        composeRule.onNodeWithText(lumi2Label).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText(classicLabel).assertIsDisplayed().assertIsNotSelected()

        composeRule.onNodeWithText(classicLabel).performClick()

        composeRule.onNodeWithText(lumi2Label).assertIsNotSelected()
        composeRule.onNodeWithText(classicLabel).assertIsSelected()
    }
}
