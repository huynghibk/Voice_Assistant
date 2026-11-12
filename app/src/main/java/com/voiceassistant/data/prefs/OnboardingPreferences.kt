package com.voiceassistant.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onboarding_settings"
)

@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keySetupDone = booleanPreferencesKey("setup_done")
    private val keySuppressSystemTtsDialog = booleanPreferencesKey("suppress_system_tts_dialog")

    val setupDone: Flow<Boolean> = context.onboardingDataStore.data
        .map { prefs -> prefs[keySetupDone] ?: false }

    val suppressSystemTtsDialog: Flow<Boolean> = context.onboardingDataStore.data
        .map { prefs -> prefs[keySuppressSystemTtsDialog] ?: false }

    suspend fun setSetupDone(done: Boolean = true) {
        context.onboardingDataStore.edit { prefs ->
            prefs[keySetupDone] = done
        }
    }

    suspend fun setSuppressSystemTtsDialog(suppress: Boolean = true) {
        context.onboardingDataStore.edit { prefs ->
            prefs[keySuppressSystemTtsDialog] = suppress
        }
    }
}
