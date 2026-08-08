package com.nuvio.tv.surpriseme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.domain.model.MetaPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RollAgainState(
    val isRolling: Boolean = false,
    val navigateTo: MetaPreview? = null,
    val message: String? = null
)

/**
 * Fork addition. Backs the "Roll again" button on a title the owner reached by rolling.
 *
 * It reads [SurpriseMeEngine.lastPickId] so the button only appears on the rolled title
 * itself - open something else and it is gone, which is what stops it turning into a
 * permanent fixture on every detail page in the app.
 */
@HiltViewModel
class SurpriseRollAgainViewModel @Inject constructor(
    private val engine: SurpriseMeEngine
) : ViewModel() {

    val lastPickId: StateFlow<String?> = engine.lastPickId

    private val _state = MutableStateFlow(RollAgainState())
    val state: StateFlow<RollAgainState> = _state.asStateFlow()

    fun rollAgain() {
        if (_state.value.isRolling) return
        _state.value = RollAgainState(isRolling = true)

        viewModelScope.launch {
            when (val result = engine.roll()) {
                is RollResult.Picked ->
                    _state.value = RollAgainState(navigateTo = result.item)
                RollResult.NoSources ->
                    _state.value = RollAgainState(message = "No recommendation addons installed.")
                RollResult.NothingLeft ->
                    _state.value = RollAgainState(message = "Nothing new left to suggest.")
                is RollResult.Failed ->
                    _state.value = RollAgainState(message = result.message)
            }
        }
    }

    fun onNavigationHandled() {
        _state.value = _state.value.copy(navigateTo = null)
    }
}
