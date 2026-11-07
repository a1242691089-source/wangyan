package com.huangder.lumibooks.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.huangder.lumibooks.translation.TranslationError
import com.huangder.lumibooks.translation.TranslationException

data class AiSettingsAction(
    val busy: Boolean = false,
    val saved: Boolean = false,
    val testReply: String? = null,
    val error: TranslationError? = null
)

sealed interface AiModelCatalog {
    data object Idle : AiModelCatalog
    data object Loading : AiModelCatalog
    data class Ready(val models: List<String>) : AiModelCatalog
    data class Failure(val error: TranslationError) : AiModelCatalog
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(private val repository: AiRepository) : ViewModel() {
    val configuration = repository.configuration.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val mutableAction = MutableStateFlow(AiSettingsAction())
    val action = mutableAction.asStateFlow()
    private var job: Job? = null
    private val mutableModels = MutableStateFlow<AiModelCatalog>(AiModelCatalog.Idle)
    val models = mutableModels.asStateFlow()
    private var modelsJob: Job? = null

    fun fetchModels(settings: AiSettings, token: String) {
        modelsJob?.cancel()
        mutableModels.value = AiModelCatalog.Loading
        modelsJob = viewModelScope.launch {
            mutableModels.value = try { AiModelCatalog.Ready(repository.models(settings, token)) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                AiModelCatalog.Failure((error as? TranslationException)?.error ?: TranslationError.SERVICE)
            }
        }
    }

    fun connectionEdited() {
        edited()
        modelsJob?.cancel()
        mutableModels.value = AiModelCatalog.Idle
    }

    fun save(settings: AiSettings, token: String) = runAction {
        repository.save(settings, token)
        AiSettingsAction(saved = true)
    }

    fun test(settings: AiSettings, token: String) = runAction {
        AiSettingsAction(testReply = repository.test(settings, token))
    }

    fun edited() { job?.cancel(); mutableAction.value = AiSettingsAction() }

    private fun runAction(block: suspend () -> AiSettingsAction) {
        job?.cancel()
        mutableAction.value = AiSettingsAction(busy = true)
        job = viewModelScope.launch {
            mutableAction.value = try { block() } catch (error: Exception) {
                if (error is CancellationException) throw error
                AiSettingsAction(error = (error as? TranslationException)?.error ?: TranslationError.STORAGE)
            }
        }
    }
}
