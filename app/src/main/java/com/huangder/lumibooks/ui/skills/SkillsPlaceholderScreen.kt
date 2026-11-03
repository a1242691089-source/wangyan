package com.huangder.lumibooks.ui.skills

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.FeaturePlaceholder
import com.huangder.lumibooks.ui.icons.AppIcons

/**
 * 技能页（占位）。
 *
 * 技能运行时依赖账号、服务端编排与工具注册，不在单机阅读器的第一批落地范围内，
 * 这里只保留导航位置与空状态说明。
 */
@Composable
fun SkillsPlaceholderScreen(modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.nav_skills),
        icon = AppIcons.SkillsTab.resolve(false),
        headline = stringResource(R.string.skills_empty_title),
        body = stringResource(R.string.skills_empty_body),
        footnote = stringResource(R.string.skills_empty_footnote),
        modifier = modifier
    )
}
