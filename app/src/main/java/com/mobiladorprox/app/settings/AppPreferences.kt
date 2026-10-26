package com.mobiladorprox.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobiladorprox.app.data.model.PerformanceProfile
import com.mobiladorprox.app.data.model.PollingRate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mobilador_prefs")

/** Preferências globais do app que sobrevivem ao fechamento (DataStore). */
class AppPreferences(private val context: Context) {

    private object Keys {
        val SELECTED_PACKAGE = stringPreferencesKey("selected_package")
        val SELECTED_GAME_LABEL = stringPreferencesKey("selected_game_label")
        val SELECTED_PROFILE_ID = longPreferencesKey("selected_profile_id")
        val MOUSE_SENSITIVITY = floatPreferencesKey("mouse_sensitivity")
        val POLLING_RATE = stringPreferencesKey("polling_rate")
        val PERFORMANCE_PROFILE = stringPreferencesKey("performance_profile")
        val MAPPER_ENABLED = booleanPreferencesKey("mapper_enabled")
    }

    val selectedPackageName: Flow<String?> = context.dataStore.data.map { it[Keys.SELECTED_PACKAGE] }
    val selectedGameLabel: Flow<String?> = context.dataStore.data.map { it[Keys.SELECTED_GAME_LABEL] }
    val selectedProfileId: Flow<Long?> = context.dataStore.data.map { it[Keys.SELECTED_PROFILE_ID] }

    suspend fun setSelectedGame(packageName: String, gameLabel: String, profileId: Long) {
        context.dataStore.edit {
            it[Keys.SELECTED_PACKAGE] = packageName
            it[Keys.SELECTED_GAME_LABEL] = gameLabel
            it[Keys.SELECTED_PROFILE_ID] = profileId
        }
    }

    suspend fun setSelectedProfileId(profileId: Long) {
        context.dataStore.edit { it[Keys.SELECTED_PROFILE_ID] = profileId }
    }

    val mouseSensitivity: Flow<Float> = context.dataStore.data.map { it[Keys.MOUSE_SENSITIVITY] ?: DEFAULT_SENSITIVITY }

    suspend fun setMouseSensitivity(value: Float) {
        context.dataStore.edit { it[Keys.MOUSE_SENSITIVITY] = value.coerceIn(1f, 20f) }
    }

    val pollingRate: Flow<PollingRate> = context.dataStore.data.map { prefs ->
        prefs[Keys.POLLING_RATE]?.let { name -> runCatching { PollingRate.valueOf(name) }.getOrNull() }
            ?: PollingRate.UNLIMITED
    }

    suspend fun setPollingRate(rate: PollingRate) {
        context.dataStore.edit { it[Keys.POLLING_RATE] = rate.name }
    }

    val performanceProfile: Flow<PerformanceProfile> = context.dataStore.data.map { prefs ->
        prefs[Keys.PERFORMANCE_PROFILE]?.let { name -> runCatching { PerformanceProfile.valueOf(name) }.getOrNull() }
            ?: PerformanceProfile.MEDIO
    }

    suspend fun setPerformanceProfile(profile: PerformanceProfile) {
        context.dataStore.edit { it[Keys.PERFORMANCE_PROFILE] = profile.name }
    }

    suspend fun restoreMouseDefaults() {
        context.dataStore.edit {
            it[Keys.MOUSE_SENSITIVITY] = DEFAULT_SENSITIVITY
            it[Keys.POLLING_RATE] = PollingRate.UNLIMITED.name
        }
    }

    val mapperEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.MAPPER_ENABLED] ?: false }

    suspend fun setMapperEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MAPPER_ENABLED] = enabled }
    }

    companion object {
        const val DEFAULT_SENSITIVITY = 5f
    }
}
