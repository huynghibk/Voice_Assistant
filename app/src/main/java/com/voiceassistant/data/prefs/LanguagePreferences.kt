package com.voiceassistant.data.prefs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voiceassistant.domain.model.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "language_settings"
)

@Singleton
class LanguagePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyLanguage = stringPreferencesKey("app_language_tag")

    val language: Flow<AppLanguage> = context.languageDataStore.data
        .map { prefs ->
            val tag = prefs[keyLanguage]
            if (tag.isNullOrBlank()) AppLanguage.fromSystem() else AppLanguage.fromTag(tag)
        }
        .distinctUntilChanged()

    suspend fun current(): AppLanguage = language.first()

    suspend fun setLanguage(language: AppLanguage) {
        context.languageDataStore.edit { prefs ->
            prefs[keyLanguage] = language.tag
        }
        applyLocale(language)
    }

    fun applyLocale(language: AppLanguage) {
        val desired = LocaleListCompat.forLanguageTags(language.tag)
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() != desired.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }
}
