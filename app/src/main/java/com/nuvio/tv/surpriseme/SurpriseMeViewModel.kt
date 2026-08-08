package com.nuvio.tv.surpriseme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which pool the owner is asking to be surprised from. */
enum class SurpriseKind { SHOW, MOVIE }

data class SurpriseMeUiState(
    val kind: SurpriseKind = SurpriseKind.SHOW,
    val includeWatched: Boolean = false,
    val isLoading: Boolean = false,
    val pick: MetaPreview? = null,
    val pickSourceName: String? = null,
    val error: String? = null,
    val hasAnySource: Boolean = true
)

@HiltViewModel
class SurpriseMeViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SurpriseMeUiState())
    val state: StateFlow<SurpriseMeUiState> = _state.asStateFlow()

    private var pressIndex = 0

    /**
     * Ids already offered in this sitting. Without it, a small recommendation catalogue
     * hands back the same title several presses running, which reads as the button being
     * broken rather than as chance.
     */
    private val alreadyOffered = mutableSetOf<String>()

    fun setKind(kind: SurpriseKind) {
        if (_state.value.kind != kind) {
            _state.value = _state.value.copy(kind = kind, pick = null, error = null)
            alreadyOffered.clear()
        }
    }

    fun setIncludeWatched(includeWatched: Boolean) {
        if (_state.value.includeWatched != includeWatched) {
            _state.value = _state.value.copy(includeWatched = includeWatched, pick = null, error = null)
            alreadyOffered.clear()
        }
    }

    fun roll() {
        if (_state.value.isLoading) return
        _state.value = _state.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            runCatching { rollOnce() }
                .onSuccess { outcome ->
                    _state.value = when (outcome) {
                        is RollOutcome.Picked -> {
                            alreadyOffered += outcome.item.id
                            pressIndex++
                            _state.value.copy(
                                isLoading = false,
                                pick = outcome.item,
                                pickSourceName = outcome.sourceName,
                                error = null,
                                hasAnySource = true
                            )
                        }
                        RollOutcome.NoSources -> _state.value.copy(
                            isLoading = false,
                            pick = null,
                            hasAnySource = false
                        )
                        RollOutcome.NothingLeft -> _state.value.copy(
                            isLoading = false,
                            error = NOTHING_LEFT
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = throwable.message ?: FAILED
                    )
                }
        }
    }

    private sealed interface RollOutcome {
        data class Picked(val item: MetaPreview, val sourceName: String) : RollOutcome
        data object NoSources : RollOutcome
        data object NothingLeft : RollOutcome
    }

    private suspend fun rollOnce(): RollOutcome {
        val contentType = when (_state.value.kind) {
            SurpriseKind.SHOW -> ContentType.SERIES
            SurpriseKind.MOVIE -> ContentType.MOVIE
        }

        // Installed, not necessarily enabled: a recommendation addon the owner keeps off
        // their home screen should still feed this button.
        val installed = addonRepository.getInstalledAddons().first()
        val bySource: Map<SurpriseSource, List<Addon>> = installed
            .mapNotNull { addon ->
                val source = sourceOf(addon) ?: return@mapNotNull null
                if (usableCatalogs(addon, contentType).isEmpty()) return@mapNotNull null
                source to addon
            }
            .groupBy({ it.first }, { it.second })

        if (bySource.isEmpty()) return RollOutcome.NoSources

        // Walk the rotation from where we left off. If the source whose turn it is has
        // nothing usable left, fall through to the next rather than failing the press.
        val order = bySource.keys
        repeat(order.size) { attempt ->
            val source = sourceForPress(pressIndex + attempt, order) ?: return RollOutcome.NoSources
            val candidates = collectCandidates(bySource[source].orEmpty(), contentType)
            val filtered = filterCandidates(candidates)
            if (filtered.isNotEmpty()) {
                return RollOutcome.Picked(filtered.random(), source.name)
            }
        }
        return RollOutcome.NothingLeft
    }

    private suspend fun collectCandidates(
        addons: List<Addon>,
        contentType: ContentType
    ): List<MetaPreview> {
        val out = mutableListOf<MetaPreview>()
        for (addon in addons) {
            for (catalog in usableCatalogs(addon, contentType)) {
                val result = catalogRepository.getCatalog(
                    addonBaseUrl = addon.baseUrl,
                    addonId = addon.id,
                    addonName = addon.name,
                    catalogId = catalog.id,
                    catalogName = catalog.name,
                    type = catalog.apiType
                ).first { it !is NetworkResult.Loading }

                if (result is NetworkResult.Success) {
                    out += result.data.items
                }
            }
        }
        return out.distinctBy { it.id }
    }

    private suspend fun filterCandidates(candidates: List<MetaPreview>): List<MetaPreview> {
        val notRepeated = candidates.filterNot { it.id in alreadyOffered }
        if (_state.value.includeWatched) return notRepeated

        // "Only new" means not already in the library - that is the signal this app
        // actually holds for a title as a whole. Per-episode watch state would not tell
        // us anything useful about a show the owner has never opened.
        return notRepeated.filterNot { item ->
            libraryRepository.isInLibrary(item.id, item.rawType).first()
        }
    }

    private companion object {
        const val NOTHING_LEFT = "Nothing new left to suggest - try Include watched."
        const val FAILED = "Could not reach your recommendation addons."
    }
}
