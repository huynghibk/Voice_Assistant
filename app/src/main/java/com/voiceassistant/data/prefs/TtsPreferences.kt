package com.voiceassistant.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voiceassistant.domain.tts.TtsBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ttsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tts_settings")

@Singleton
class TtsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyBackend = stringPreferencesKey("tts_backend")

    val backend: Flow<TtsBackend> = context.ttsDataStore.data
        .map { prefs -> TtsBackend.fromStorageKey(prefs[keyBackend]) }
        .distinctUntilChanged()

    suspend fun currentBackend(): TtsBackend = backend.first()

    suspend fun setBackend(backend: TtsBackend) {
        context.ttsDataStore.edit { prefs -> prefs[keyBackend] = backend.storageKey }
    }
}
