package com.huangder.lumibooks.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.settings.AppAccentPresetRow
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 只负责写主题色。
 *
 * 「我的」页与设置页共用同一个 `app_accent_color` 存储键，因此这里不缓存第二份状态，
 * 选中态直接从 DataStore 流读，保证在任一处改色，另一处立刻同步。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    val appAccentColor: StateFlow<String> = dataStoreManager.appAccentColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DEFAULT_APP_ACCENT_HEX
    )

    fun selectAccentColor(hex: String) {
        viewModelScope.launch { dataStoreManager.saveAppAccentColor(hex) }
    }
}

/**
 * 「我的」页：主题预设 + 二级入口。
 *
 * 统计与书库管理从底部栏移到这里的二级入口，所以这里的入口做成整行可点、
 * 带图标与说明文字，而不是折叠进某个菜单。
 */
@Composable
fun ProfileScreen(
    onOpenStatistics: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val homeState by homeViewModel.uiState.collectAsState()
    val accentHex by profileViewModel.appAccentColor.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.nav_profile),
            fontSize = AppType.Display,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
        )

        ProfileHeaderCard(
            avatarUri = homeState.avatarUri,
            booksCount = homeState.books.size,
            dailyGoalMinutes = homeState.dailyGoal
        )

        Spacer(Modifier.height(AppSpace.lg))

        Text(
            text = stringResource(R.string.label_app_accent_preset),
            fontSize = AppType.Caption,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(start = AppSpace.lg, end = AppSpace.lg)
        )
        AppAccentPresetRow(
            selectedHex = accentHex,
            onSelect = profileViewModel::selectAccentColor
        )

        Spacer(Modifier.height(AppSpace.lg))

        ProfileEntryRow(
            icon = AppIcons.StatisticsTab.resolve(false),
            title = stringResource(R.string.statistics_title),
            onClick = onOpenStatistics
        )
        ProfileEntryRow(
            icon = AppIcons.BookshelfTab.resolve(false),
            title = stringResource(R.string.bookshelf_manage),
            subtitle = stringResource(R.string.bookshelf_manage_desc),
            onClick = onOpenLibrary
        )
        ProfileEntryRow(
            icon = AppIcons.Gear,
            title = stringResource(R.string.settings),
            onClick = {
                context.startActivity(
                    android.content.Intent(
                        context,
                        com.huangder.lumibooks.ui.settings.SettingsActivity::class.java
                    )
                )
            }
        )

        Spacer(Modifier.height(AppSpace.xl))
    }
}

@Composable
private fun ProfileHeaderCard(
    avatarUri: String?,
    booksCount: Int,
    dailyGoalMinutes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .padding(AppSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(AppColors.BgGray),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri != null) {
                AsyncImage(
                    model = java.io.File(avatarUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = AppIcons.ProfileTab.resolve(false),
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(Modifier.width(AppSpace.md))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = AppType.Section,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.profile_books_count, booksCount) +
                    " · " +
                    stringResource(R.string.profile_daily_goal, dailyGoalMinutes),
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun ProfileEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpace.lg, vertical = AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppSpace.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontSize = AppType.Body, color = AppColors.TextPrimary)
            if (subtitle != null) {
                Text(text = subtitle, fontSize = AppType.Caption, color = AppColors.TextSecondary)
            }
        }
        Icon(
            imageVector = AppIcons.ArrowRight,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}
