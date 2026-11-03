package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType

/**
 * 尚未实现的功能页统一空状态。
 *
 * 知识页与 Skills 页现在只有结构：标题 + 空状态说明 + 一行"后续版本开放"的脚注。
 * 放在这里而不是各写一遍，是为了两页的视觉与文案节奏保持一致；
 * 等它们有真实内容时，替换掉内容区即可，外层骨架不用动。
 */
@Composable
fun FeaturePlaceholder(
    title: String,
    icon: ImageVector,
    headline: String,
    body: String,
    footnote: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = title,
            fontSize = AppType.Display,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AppColors.BgGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(AppSpace.lg))
            Text(
                text = headline,
                fontSize = AppType.Section,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(AppSpace.sm))
            Text(
                text = body,
                fontSize = AppType.BodySmall,
                lineHeight = 22.sp,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(AppSpace.lg))
            Text(
                text = footnote,
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(AppColors.BgGray)
                    .padding(horizontal = AppSpace.md, vertical = AppSpace.sm)
            )
        }
    }
}
