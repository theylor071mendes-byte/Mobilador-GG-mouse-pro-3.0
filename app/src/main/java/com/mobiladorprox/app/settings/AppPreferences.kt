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
        val FREE_LOOK_SPEED_X = floatPreferencesKey("free_look_speed_x")
        val FREE_LOOK_SPEED_Y = floatPreferencesKey("free_look_speed_y")
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

    /** Velocidade horizontal da câmera no controle livre ("Suspender mouse"), 0 a 1000; 500 = padrão. */
    val freeLookSpeedX: Flow<Float> = context.dataStore.data.map { it[Keys.FREE_LOOK_SPEED_X] ?: DEFAULT_FREE_LOOK_SPEED }

    /** Velocidade vertical da puxada (olhar para cima/baixo) no controle livre, 0 a 1000; 500 = padrão. */
    val freeLookSpeedY: Flow<Float> = context.dataStore.data.map { it[Keys.FREE_LOOK_SPEED_Y] ?: DEFAULT_FREE_LOOK_SPEED }

    suspend fun setFreeLookSpeedX(value: Float) {
        context.dataStore.edit { it[Keys.FREE_LOOK_SPEED_X] = value.coerceIn(0f, 1000f) }
    }

    suspend fun setFreeLookSpeedY(value: Float) {
        context.dataStore.edit { it[Keys.FREE_LOOK_SPEED_Y] = value.coerceIn(0f, 1000f) }
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
        const val DEFAULT_FREE_LOOK_SPEED = 500f
    }
}
