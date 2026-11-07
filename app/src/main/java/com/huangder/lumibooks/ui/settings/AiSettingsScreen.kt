package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ai.AiConfiguration
import com.huangder.lumibooks.ai.AiModelCatalog
import com.huangder.lumibooks.ai.AiSettings
import com.huangder.lumibooks.ai.AiSettingsAction
import com.huangder.lumibooks.ai.AiSettingsViewModel
import com.huangder.lumibooks.translation.TranslationAuth
import com.huangder.lumibooks.translation.TranslationError
import com.huangder.lumibooks.translation.TranslationProvider
import com.huangder.lumibooks.translation.messageResource
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType

/**
 * AI 设置（V0.5a）：配置通用 AI 服务（OpenAI 兼容），供后续阅读问答等能力使用。
 * 与 AI 翻译设置页同构但独立——各存各的配置与密钥。
 */

/** AI 语境下的错误文案：配置与空响应有专属文案，其余与翻译共用同一组通用描述。 */
@androidx.annotation.StringRes
internal fun aiMessageResource(error: TranslationError): Int = when (error) {
    TranslationError.CONFIGURATION -> R.string.ai_error_configuration
    TranslationError.EMPTY_RESPONSE -> R.string.ai_error_empty
    else -> error.messageResource()
}
@Composable
fun AiSettingsDetail(viewModel: AiSettingsViewModel = hiltViewModel()) {
    val configuration by viewModel.configuration.collectAsState()
    val action by viewModel.action.collectAsState()
    val models by viewModel.models.collectAsState()
    val config = configuration ?: run { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    AiSettingsContent(config, action, viewModel::save, viewModel::test, viewModel::edited,
        models, viewModel::fetchModels, viewModel::connectionEdited)
}

@Composable
internal fun AiSettingsContent(
    config: AiConfiguration,
    action: AiSettingsAction,
    onSave: (AiSettings, String) -> Unit,
    onTest: (AiSettings, String) -> Unit,
    onEdited: () -> Unit,
    models: AiModelCatalog = AiModelCatalog.Idle,
    onFetchModels: (AiSettings, String) -> Unit = { _, _ -> },
    onConnectionEdited: () -> Unit = onEdited
) {
    var draftJson by rememberSaveable { mutableStateOf(config.settings.copy(enabled = config.enabled).toJson()) }
    val draft = AiSettings.fromJson(draftJson)
    // Never put the API key into saved instance state or a composable's saveable state.
    var token by remember { mutableStateOf("") }
    fun update(value: AiSettings) {
        val connectionChanged = value.credentialScope != draft.credentialScope || value.allowHttp != draft.allowHttp || value.provider != draft.provider
        draftJson = value.toJson()
        if (connectionChanged) onConnectionEdited() else onEdited()
    }
    LaunchedEffect(action.saved) { if (action.saved) token = "" }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.ai_description), color = AppColors.TextSecondary, fontSize = AppType.Body)
        SettingsSectionCard {
            SettingsChoiceRow(stringResource(R.string.translation_provider), when (draft.provider) {
                TranslationProvider.MIMO -> "MiMo"
                TranslationProvider.DEEPSEEK -> "DeepSeek"
                TranslationProvider.OPENAI -> "OpenAI"
                TranslationProvider.CUSTOM -> stringResource(R.string.translation_custom)
            }, listOf("MiMo", "DeepSeek", "OpenAI", stringResource(R.string.translation_custom)), !action.busy) { index ->
                val provider = TranslationProvider.entries[index]
                if (provider != draft.provider) {
                    token = ""
                    update(draft.copy(provider = provider, baseUrl = provider.baseUrl, auth = provider.auth, model = ""))
                }
            }
            SettingsInputField("Base URL", draft.baseUrl, { update(draft.copy(baseUrl = it)) },
                enabled = !action.busy, keyboardType = KeyboardType.Uri,
                hint = stringResource(R.string.translation_url_hint), tag = "ai-base-url")
            if (draft.provider == TranslationProvider.CUSTOM) {
                SettingsChoiceRow(stringResource(R.string.translation_auth),
                    if (draft.auth == TranslationAuth.BEARER) "Bearer" else "api-key",
                    listOf("Bearer", "api-key"), !action.busy) { update(draft.copy(auth = TranslationAuth.entries[it])) }
            }
            SettingsInputField("API Key", token, { token = it; onConnectionEdited() }, enabled = !action.busy,
                keyboardType = KeyboardType.Password, secret = true, tag = "ai-api-key",
                hint = stringResource(if (config.hasToken && config.settings.credentialScope == draft.credentialScope)
                    R.string.translation_key_saved else R.string.translation_key_hint))
            SettingsModelField(
                value = draft.model, onChange = { update(draft.copy(model = it)) }, enabled = !action.busy,
                catalogModels = (models as? AiModelCatalog.Ready)?.models.orEmpty(),
                fetching = models == AiModelCatalog.Loading,
                fetchErrorText = (models as? AiModelCatalog.Failure)?.let { stringResource(aiMessageResource(it.error)) },
                manualHintText = stringResource(R.string.translation_models_manual),
                labels = ModelFieldLabels(
                    model = stringResource(R.string.translation_model),
                    testTag = "ai-model",
                    choose = stringResource(R.string.translation_choose_model),
                    fetch = stringResource(R.string.translation_fetch_models),
                    fetching = stringResource(R.string.translation_fetching_models)
                ),
                onFetch = { onFetchModels(draft, token) }
            )
            SettingsToggleRow(stringResource(R.string.translation_allow_http), draft.allowHttp, !action.busy) {
                update(draft.copy(allowHttp = it))
            }
        }
        SettingsSectionCard {
            Text(stringResource(R.string.ai_consent), color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
            SettingsToggleRow(stringResource(R.string.ai_enable), draft.enabled, !action.busy) {
                update(draft.copy(enabled = it))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidGlassTextButton(stringResource(R.string.translation_save), { onSave(draft, token) }, enabled = !action.busy)
            LiquidGlassTextButton(stringResource(R.string.ai_test), { onTest(draft, token) }, enabled = !action.busy)
            if (action.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (action.saved) Text(stringResource(R.string.translation_saved), color = AppColors.TextSecondary)
            action.error?.let { Text(stringResource(aiMessageResource(it)), color = AppColors.TextSecondary) }
            action.testReply?.let { Text(it, color = AppColors.TextPrimary, fontSize = AppType.Body) }
        }
    }
}
