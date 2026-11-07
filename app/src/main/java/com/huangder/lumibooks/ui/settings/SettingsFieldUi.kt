package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppType

/**
 * 设置页通用字段组件：由 AI 翻译设置页与 AI 设置页（V0.5a）共享。
 * 视觉与交互规范与各设置详情页保持一致（液态玻璃卡片 + 菜单锚点）。
 */

@Composable
internal fun SettingsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppRoundedCornerShape(AppRadius.md))
            .lumiCardSurface(shape = AppRoundedCornerShape(AppRadius.md))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
internal fun SettingsToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = AppColors.TextPrimary, fontSize = AppType.Body)
        LiquidGlassSwitch(checked, onChange, enabled = enabled)
    }
}

@Composable
internal fun SettingsChoiceRow(label: String, value: String, choices: List<String>, enabled: Boolean, onSelect: (Int) -> Unit) {
    val host = LocalLiquidGlassMenuHost.current
    val sourceId = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val width = with(LocalDensity.current) { bounds.width.toDp() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val shape = AppRoundedCornerShape(AppRadius.lg)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .liquidGlassMenuAnchor(sourceId, cornerRadius = AppRadius.lg)
            .clip(shape).lumiCardSurface(color = AppColors.BgGray, shape = shape, controlEdge = true)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clickable(enabled = enabled && host != null, role = Role.Button,
                interactionSource = remember { MutableInteractionSource() }, indication = null) {
                keyboard?.hide()
                focus.clearFocus()
                host?.toggle(LiquidGlassMenuSpec(anchorBounds = bounds, width = width,
                    sourceId = sourceId, anchorCornerRadius = AppRadius.lg,
                    items = choices.mapIndexed { index, choice ->
                        LiquidGlassMenuItem(label = choice, selected = choice == value, onClick = { onSelect(index) })
                    }))
                }
            .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(value, Modifier.weight(1f), color = AppColors.TextPrimary, fontSize = AppType.Body,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(AppIcons.CaretDown, null, Modifier.size(18.dp), tint = AppColors.TextSecondary)
        }
    }
}

@Composable
internal fun SettingsInputField(
    label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean,
    tag: String, hint: String? = null, keyboardType: KeyboardType = KeyboardType.Ascii, secret: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
        SettingsEditableField(label, value, onValueChange, enabled, tag, Modifier.fillMaxWidth(), keyboardType, secret)
        hint?.let { Text(it, color = AppColors.TextSecondary, fontSize = AppType.Caption) }
    }
}

@Composable
internal fun SettingsEditableField(
    label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean,
    tag: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Ascii,
    secret: Boolean = false, trailingContent: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = AppRoundedCornerShape(AppRadius.lg)
    BasicTextField(value, onValueChange, enabled = enabled, singleLine = true,
            modifier = modifier.testTag(tag).semantics { contentDescription = label }
                .onFocusChanged { focused = it.isFocused }
                .clip(shape).lumiCardSurface(color = AppColors.BgGray, shape = shape, controlEdge = true)
                .border(1.dp, if (focused) AppColors.Accent.copy(alpha = 0.45f) else Color.Transparent, shape),
            textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary, fontSize = AppType.Body),
            cursorBrush = SolidColor(AppColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { innerTextField ->
                Row(Modifier.heightIn(min = 54.dp).padding(start = 16.dp, end = if (trailingContent == null) 16.dp else 4.dp)
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) { innerTextField() }
                    trailingContent?.invoke()
                }
            })
}

@Composable
internal fun SettingsModelField(
    value: String, onChange: (String) -> Unit, enabled: Boolean,
    catalogModels: List<String>, fetching: Boolean, fetchErrorText: String?, manualHintText: String?,
    labels: ModelFieldLabels, onFetch: () -> Unit
) {
    val host = LocalLiquidGlassMenuHost.current
    val sourceId = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val width = with(LocalDensity.current) { bounds.width.toDp() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(labels.model, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsEditableField(labels.model, value, onChange, enabled, labels.testTag,
                Modifier.weight(1f).liquidGlassMenuAnchor(sourceId, cornerRadius = AppRadius.lg)
                    .onGloballyPositioned { bounds = it.boundsInRoot() },
                trailingContent = if (catalogModels.isEmpty()) null else {
                    {
                        LiquidGlassIconButton(AppIcons.CaretDown, labels.choose, {
                            keyboard?.hide(); focus.clearFocus()
                            host?.toggle(LiquidGlassMenuSpec(anchorBounds = bounds, width = width,
                                sourceId = sourceId, anchorCornerRadius = AppRadius.lg,
                                items = catalogModels.map { name -> LiquidGlassMenuItem(name,
                                    selected = name == value, onClick = { onChange(name) }) }))
                        }, size = 36.dp, iconSize = 18.dp, enabled = enabled && host != null)
                    }
                })
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                if (fetching) {
                    androidx.compose.material3.CircularProgressIndicator(
                        Modifier.size(18.dp).semantics { contentDescription = labels.fetching },
                        strokeWidth = 1.5.dp, color = AppColors.TextSecondary)
                } else LiquidGlassIconButton(AppIcons.DownloadSimple, labels.fetch,
                    onFetch, size = 52.dp, iconSize = 22.dp, enabled = enabled, normalContainerColor = AppColors.BgGray)
            }
        }
        if (fetchErrorText != null) {
            Text(fetchErrorText, color = AppColors.TextSecondary, fontSize = AppType.Caption)
        }
        if (manualHintText != null && fetchErrorText != null) {
            Text(manualHintText, color = AppColors.TextSecondary, fontSize = AppType.Caption)
        }
    }
}

internal data class ModelFieldLabels(
    val model: String,
    val testTag: String,
    val choose: String,
    val fetch: String,
    val fetching: String
)
