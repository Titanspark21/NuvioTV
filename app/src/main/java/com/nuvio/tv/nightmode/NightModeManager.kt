package com.nuvio.tv.nightmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class NightModeState(
    val enabled: Boolean = false,
    val strength: Int = NightModeDataStore.DEFAULT_STRENGTH
)

/**
 * Adaptive brightness measurement (e.g. video surface pixel sampling) was considered,
 * but it is not possible because the video renders on an Android SurfaceView whose pixels
 * cannot be read back by the application process. Therefore, a user-adjustable static
 * translucent black overlay is used.
 */
@Singleton
class NightModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: NightModeDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val state: StateFlow<NightModeState> = combine(
        dataStore.enabled,
        dataStore.strength
    ) { enabled, strength ->
        NightModeState(enabled = enabled, strength = strength)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = NightModeState()
    )

    init {
        scope.launch {
            checkAutoOff()
            while (true) {
                delay(60_000L)
                checkAutoOff()
            }
        }

        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
            }
            // targetSdk 34+ refuses a runtime receiver that does not declare its export
            // status, so without this flag the registration throws and the clock and
            // timezone broadcasts are silently never delivered.
            ContextCompat.registerReceiver(
                context,
                object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        scope.launch { checkAutoOff() }
                    }
                },
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private suspend fun checkAutoOff() {
        val isEnabled = dataStore.enabled.first()
        if (isEnabled) {
            val enabledAt = dataStore.enabledAtMillis.first()
            if (NightModeClockUtils.isExpired(enabledAt)) {
                dataStore.setEnabled(false)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        scope.launch {
            dataStore.setEnabled(enabled)
        }
    }

    fun setStrength(strength: Int) {
        scope.launch {
            dataStore.setStrength(strength)
        }
    }
}
