package com.voiceassistant

import android.app.Application
import com.voiceassistant.data.prefs.LanguagePreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VoiceAssistantApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LanguageEntryPoint {
        fun languagePreferences(): LanguagePreferences
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = EntryPointAccessors.fromApplication(
            this,
            LanguageEntryPoint::class.java
        ).languagePreferences()

        appScope.launch {
            runCatching { prefs.applyLocale(prefs.current()) }
            prefs.language.collect { lang ->
                prefs.applyLocale(lang)
            }
        }
    }
}
