package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.translation.*
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType

@Composable
fun TranslationSettingsDetail(viewModel: TranslationSettingsViewModel = hiltViewModel()) {
    val configuration by viewModel.configuration.collectAsState()
    val action by viewModel.action.collectAsState()
    val models by viewModel.models.collectAsState()
    val config = configuration ?: run { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    TranslationSettingsContent(config, action, viewModel::save, viewModel::test, viewModel::edited,
        models, viewModel::fetchModels, viewModel::connectionEdited)
}

@Composable
internal fun TranslationSettingsContent(
    config: TranslationConfiguration,
    action: TranslationSettingsAction,
    onSave: (TranslationSettings, String) -> Unit,
    onTest: (TranslationSettings, String) -> Unit,
    onEdited: () -> Unit,
    models: TranslationModelCatalog = TranslationModelCatalog.Idle,
    onFetchModels: (TranslationSettings, String) -> Unit = { _, _ -> },
    onConnectionEdited: () -> Unit = onEdited
) {
    var draftJson by rememberSaveable { mutableStateOf(config.settings.copy(enabled = config.enabled).toJson()) }
    val draft = TranslationSettings.fromJson(draftJson)
    // Never put the API key into saved instance state or a composable's saveable state.
    var token by remember { mutableStateOf("") }
    val languages = listOf("简体中文", "繁體中文", "English", "日本語", "한국어")
    var customLanguage by rememberSaveable { mutableStateOf(draft.targetLanguage !in languages) }
    fun update(value: TranslationSettings) {
        val connectionChanged = value.credentialScope != draft.credentialScope || value.allowHttp != draft.allowHttp || value.provider != draft.provider
        draftJson = value.toJson()
        if (connectionChanged) onConnectionEdited() else onEdited()
    }
    LaunchedEffect(action.saved) { if (action.saved) token = "" }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.translation_description), color = AppColors.TextSecondary, fontSize = AppType.Body)
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
                hint = stringResource(R.string.translation_url_hint), tag = "translation-base-url")
            if (draft.provider == TranslationProvider.CUSTOM) {
                SettingsChoiceRow(stringResource(R.string.translation_auth),
                    if (draft.auth == TranslationAuth.BEARER) "Bearer" else "api-key",
                    listOf("Bearer", "api-key"), !action.busy) { update(draft.copy(auth = TranslationAuth.entries[it])) }
            }
            SettingsInputField("API Key", token, { token = it; onConnectionEdited() }, enabled = !action.busy,
                keyboardType = KeyboardType.Password, secret = true, tag = "translation-api-key",
                hint = stringResource(if (config.hasToken && config.settings.credentialScope == draft.credentialScope)
                    R.string.translation_key_saved else R.string.translation_key_hint))
            TranslationModelInput(draft.model, { update(draft.copy(model = it)) }, !action.busy, models,
                onFetch = { onFetchModels(draft, token) })
            SettingsToggleRow(stringResource(R.string.translation_allow_http), draft.allowHttp, !action.busy) {
                update(draft.copy(allowHttp = it))
            }
        }
        val custom = stringResource(R.string.translation_custom)
        SettingsSectionCard {
            SettingsChoiceRow(stringResource(R.string.translation_language), if (customLanguage) custom else draft.targetLanguage,
                languages + custom, !action.busy) { index ->
                val useCustom = index == languages.size
                if (!useCustom || !customLanguage) update(draft.copy(targetLanguage = if (useCustom) "" else languages[index]))
                customLanguage = useCustom
            }
            if (customLanguage) SettingsInputField(stringResource(R.string.translation_language), draft.targetLanguage,
                { update(draft.copy(targetLanguage = it)) }, enabled = !action.busy, tag = "translation-custom-language",
                keyboardType = KeyboardType.Text)
            Text(stringResource(R.string.translation_consent), color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
            SettingsToggleRow(stringResource(R.string.translation_enable), draft.enabled, !action.busy) {
                update(draft.copy(enabled = it))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidGlassTextButton(stringResource(R.string.translation_save), { onSave(draft, token) }, enabled = !action.busy)
            LiquidGlassTextButton(stringResource(R.string.translation_test), { onTest(draft, token) }, enabled = !action.busy)
            if (action.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (action.saved) Text(stringResource(R.string.translation_saved), color = AppColors.TextSecondary)
            action.error?.let { Text(stringResource(it.messageResource()), color = AppColors.TextSecondary) }
            action.testTranslation?.let { Text(it, color = AppColors.TextPrimary, fontSize = AppType.Body) }
        }
    }
}

@Composable
private fun TranslationModelInput(value: String, onChange: (String) -> Unit, enabled: Boolean,
    catalog: TranslationModelCatalog, onFetch: () -> Unit) {
    SettingsModelField(
        value = value, onChange = onChange, enabled = enabled,
        catalogModels = (catalog as? TranslationModelCatalog.Ready)?.models.orEmpty(),
        fetching = catalog == TranslationModelCatalog.Loading,
        fetchErrorText = (catalog as? TranslationModelCatalog.Failure)
            ?.let { stringResource(it.error.messageResource()) },
        manualHintText = stringResource(R.string.translation_models_manual),
        labels = ModelFieldLabels(
            model = stringResource(R.string.translation_model),
            testTag = "translation-model",
            choose = stringResource(R.string.translation_choose_model),
            fetch = stringResource(R.string.translation_fetch_models),
            fetching = stringResource(R.string.translation_fetching_models)
        ),
        onFetch = onFetch
    )
}
