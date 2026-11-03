package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppStroke

/**
 * 发丝分隔线。0.5dp 粗细 + 18% 文字色，取自书架分类页原本私有的 CategoryRowDivider，
 * 抽成公共组件供新界面统一使用。
 *
 * 说明：`@Composable` 里读 [AppColors.TextSecondary]，所以深色 / e-ink 模式下会跟随主题自动变化，
 * 不需要调用方各自判断。
 *
 * 边界：存量界面里还有数十处手写的 `Box(Modifier.height(1.dp).background(AppColors.Divider))`，
 * 本组件不负责批量替换它们，只保证新代码不再各写一份。
 */
@Composable
fun WangyanDivider(
    modifier: Modifier = Modifier,
    startPadding: Dp = 0.dp,
    endPadding: Dp = 0.dp
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startPadding, end = endPadding),
        thickness = AppStroke.Hairline,
        color = AppColors.TextSecondary.copy(alpha = 0.18f)
    )
}
