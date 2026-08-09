package com.nuvio.tv.addonspeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddonSpeedUiState(
    val metaStats: List<AddonSpeedStats> = emptyList(),
    val streamStats: List<AddonSpeedStats> = emptyList(),
    val isTesting: Boolean = false,
    val testProgress: String? = null,
    val testRows: List<SpeedTestRow> = emptyList(),
    val items: List<SpeedTestItem> = SpeedTestItem.DEFAULTS
)

@HiltViewModel
class AddonSpeedViewModel @Inject constructor(
    private val speedLog: AddonSpeedLog,
    private val speedTest: AddonSpeedTest
) : ViewModel() {

    private val _state = MutableStateFlow(AddonSpeedUiState())
    val state: StateFlow<AddonSpeedUiState> = _state.asStateFlow()

    private var testJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                metaStats = speedLog.stats(AddonCallKind.META),
                streamStats = speedLog.stats(AddonCallKind.STREAM)
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            speedLog.clear()
            refresh()
        }
    }

    fun setItemId(index: Int, id: String) {
        val items = _state.value.items.toMutableList()
        val existing = items.getOrNull(index) ?: return
        items[index] = existing.copy(id = id.trim())
        _state.value = _state.value.copy(items = items)
    }

    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        _state.value = _state.value.copy(isTesting = false, testProgress = null)
    }

    /**
     * Runs the whole sweep: every metadata addon against every title, then one stream
     * search per title. Results append as they arrive so the screen fills in rather than
     * sitting blank for a minute.
     */
    fun runTest() {
        if (_state.value.isTesting) return
        testJob?.cancel()
        _state.value = _state.value.copy(isTesting = true, testRows = emptyList(), testProgress = null)

        testJob = viewModelScope.launch {
            val rows = mutableListOf<SpeedTestRow>()
            val items = _state.value.items.filter { it.id.isNotBlank() }

            val metaAddons = speedTest.metaAddons()
            for (item in items) {
                for (addon in metaAddons) {
                    _state.value = _state.value.copy(
                        testProgress = "${addon.displayName} - ${item.label}"
                    )
                    rows += speedTest.timeMeta(addon, item)
                    _state.value = _state.value.copy(testRows = rows.toList())
                }
            }

            for (item in items) {
                _state.value = _state.value.copy(testProgress = "Streams - ${item.label}")
                rows += speedTest.timeStreams(item)
                _state.value = _state.value.copy(testRows = rows.toList())
            }

            _state.value = _state.value.copy(isTesting = false, testProgress = null)
            // The test's own requests were recorded like any other, so the history is
            // now out of date on screen.
            refresh()
        }
    }
}
