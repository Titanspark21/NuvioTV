package com.nuvio.tv.nightmode

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NightModeDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "night_mode_settings"
        const val DEFAULT_STRENGTH = 35
        const val MIN_STRENGTH = 0
        const val MAX_STRENGTH = 70
    }

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private val enabledKey = booleanPreferencesKey("night_mode_enabled")
    private val strengthKey = intPreferencesKey("night_mode_strength")
    private val enabledAtKey = longPreferencesKey("night_mode_enabled_at")

    val enabled: Flow<Boolean> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            prefs[enabledKey] ?: false
        }
    }

    val strength: Flow<Int> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            (prefs[strengthKey] ?: DEFAULT_STRENGTH).coerceIn(MIN_STRENGTH, MAX_STRENGTH)
        }
    }

    val enabledAtMillis: Flow<Long> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            prefs[enabledAtKey] ?: 0L
        }
    }

    suspend fun setEnabled(enabled: Boolean, timestampMillis: Long = System.currentTimeMillis()) {
        store().edit { prefs ->
            prefs[enabledKey] = enabled
            if (enabled) {
                prefs[enabledAtKey] = timestampMillis
            } else {
                prefs[enabledAtKey] = 0L
            }
        }
    }

    suspend fun setStrength(strengthPercent: Int) {
        val clamped = strengthPercent.coerceIn(MIN_STRENGTH, MAX_STRENGTH)
        store().edit { prefs ->
            prefs[strengthKey] = clamped
        }
    }
}
