package com.huangder.lumibooks.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 自真机迁移（原 SettingsRowsInstrumentedTest）：规避 Android 16 真机
// androidx.test EmptyActivity 窗口舞步的随机楔死竞态。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w369dp-h822dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRowsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigationSwitchAndDangerRowsInvokeCallbacks() {
        var navigated = false
        var checked by mutableStateOf(false)
        var deleted = false
        composeRule.setContent {
            EBookReaderTheme(darkTheme = false) {
                Column {
                    SettingsNavigationRow("Navigation") { navigated = true }
                    SettingsSwitchRow("Switch", checked = checked, onCheckedChange = { checked = it })
                    StorageActionRow(AppIcons.Trash, "Delete", destructive = true) { deleted = true }
                }
            }
        }

        composeRule.onNodeWithText("Navigation").performClick()
        composeRule.onNodeWithText("Switch").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle {
            assertTrue(navigated)
            assertTrue(checked)
            assertTrue(deleted)
        }
    }

    @Test
    fun sliderDropdownAndRadioRowsUpdateSelection() {
        var sliderValue = 5f
        var dropdownValue by mutableStateOf("first")
        var selected = false
        composeRule.setContent {
            EBookReaderTheme(darkTheme = false) {
                Column {
                    SettingsSliderItem(
                        label = "Slider",
                        value = sliderValue,
                        range = 0f..10f,
                        valueText = sliderValue.toString(),
                        onChange = { sliderValue = it }
                    )
                    DropdownSettingRow(
                        label = "Dropdown",
                        options = listOf("first" to "First", "second" to "Second"),
                        selected = dropdownValue,
                        onSelect = { dropdownValue = it }
                    )
                    MineruModeCard("Radio", "Description", selected) { selected = true }
                }
            }
        }

        val slider = SemanticsMatcher("settings slider") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null
        }
        composeRule.onNode(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
        composeRule.onNodeWithText("First").performClick()
        composeRule.onNodeWithText("Second").performClick()
        composeRule.onNodeWithText("Radio").performClick()

        composeRule.runOnIdle {
            assertEquals(8f, sliderValue, 0.01f)
            assertEquals("second", dropdownValue)
            assertTrue(selected)
        }
    }
}
