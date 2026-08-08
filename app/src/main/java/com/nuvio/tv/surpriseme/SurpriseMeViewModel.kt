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

enum class SurpriseStep { KIND, FILTER, ROLLING }

data class SurpriseMeUiState(
    val step: SurpriseStep = SurpriseStep.KIND,
    val moviePosters: List<String> = emptyList(),
    val showPosters: List<String> = emptyList(),
    val message: String? = null,
    val navigateTo: MetaPreview? = null
)

@HiltViewModel
class SurpriseMeViewModel @Inject constructor(
    private val engine: SurpriseMeEngine
) : ViewModel() {

    private val _state = MutableStateFlow(SurpriseMeUiState())
    val state: StateFlow<SurpriseMeUiState> = _state.asStateFlow()

    private var kind: SurpriseKind = SurpriseKind.SHOW

    init {
        // Fetched per side so one slow addon cannot hold up the other wall of artwork.
        viewModelScope.launch {
            val posters = engine.samplePosters(SurpriseKind.MOVIE)
            _state.value = _state.value.copy(moviePosters = posters)
        }
        viewModelScope.launch {
            val posters = engine.samplePosters(SurpriseKind.SHOW)
            _state.value = _state.value.copy(showPosters = posters)
        }
    }

    fun chooseKind(kind: SurpriseKind) {
        this.kind = kind
        _state.value = _state.value.copy(step = SurpriseStep.FILTER, message = null)
    }

    fun chooseFilter(includeWatched: Boolean) {
        engine.beginSession(kind, includeWatched)
        _state.value = _state.value.copy(step = SurpriseStep.ROLLING, message = null)

        viewModelScope.launch {
            when (val result = engine.roll()) {
                is RollResult.Picked -> _state.value = _state.value.copy(navigateTo = result.item)
                RollResult.NoSources -> backToStart(NO_SOURCES)
                RollResult.NothingLeft -> backToStart(NOTHING_LEFT)
                is RollResult.Failed -> backToStart(result.message)
            }
        }
    }

    fun onNavigationHandled() {
        _state.value = _state.value.copy(navigateTo = null)
    }

    private fun backToStart(message: String) {
        _state.value = _state.value.copy(step = SurpriseStep.KIND, message = message)
    }

    private companion object {
        const val NO_SOURCES = "Install BingeCat, Simkl or Trakt to use this."
        const val NOTHING_LEFT = "Nothing new left to suggest - try Include watched."
    }
}
