package com.mobiladorprox.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mobilador_prefs")

/** Preferências simples do app que sobrevivem ao fechamento (DataStore). */
class AppPreferences(private val context: Context) {

    private object Keys {
        val LAST_PROFILE_ID = longPreferencesKey("last_profile_id")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val MAPPER_ENABLED = booleanPreferencesKey("mapper_enabled")
    }

    val lastProfileId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_PROFILE_ID]
    }

    suspend fun setLastProfileId(id: Long) {
        context.dataStore.edit { it[Keys.LAST_PROFILE_ID] = id }
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DONE] ?: false
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    val mapperEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.MAPPER_ENABLED] ?: false
    }

    suspend fun setMapperEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MAPPER_ENABLED] = enabled }
    }
}
