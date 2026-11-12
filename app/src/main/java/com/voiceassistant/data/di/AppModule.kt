package com.voiceassistant.data.di

import android.content.Context
import androidx.room.Room
import com.voiceassistant.data.local.db.AppDatabase
import com.voiceassistant.data.local.db.ProjectFtsDao
import com.voiceassistant.data.local.db.SearchHistoryDao
import com.voiceassistant.data.local.db.TextProjectDao
import com.voiceassistant.data.local.db.VoiceModelDao
import com.voiceassistant.data.repository.ProjectRepositoryImpl
import com.voiceassistant.data.repository.VoiceRepositoryImpl
import com.voiceassistant.domain.playback.PlaybackController
import com.voiceassistant.domain.repository.ProjectRepository
import com.voiceassistant.domain.repository.TranslationRepository
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.resource.ResourcePackManager
import com.voiceassistant.domain.resource.TtsResourceGuard
import com.voiceassistant.domain.translation.LanguageIdentifier
import com.voiceassistant.engine.playback.LocalPlaybackController
import com.voiceassistant.engine.resource.ResourcePackManagerImpl
import com.voiceassistant.engine.resource.TtsResourceGuardImpl
import com.voiceassistant.engine.translation.MlKitLanguageIdentifier
import com.voiceassistant.engine.translation.TranslationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "voice_assistant.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideVoiceModelDao(db: AppDatabase): VoiceModelDao = db.voiceModelDao()

    @Provides
    fun provideTextProjectDao(db: AppDatabase): TextProjectDao = db.textProjectDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    fun provideProjectFtsDao(db: AppDatabase): ProjectFtsDao = db.projectFtsDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVoiceRepository(impl: VoiceRepositoryImpl): VoiceRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindTranslationRepository(impl: TranslationRepositoryImpl): TranslationRepository

    @Binds
    @Singleton
    abstract fun bindResourcePackManager(impl: ResourcePackManagerImpl): ResourcePackManager

    @Binds
    @Singleton
    abstract fun bindTtsResourceGuard(impl: TtsResourceGuardImpl): TtsResourceGuard

    @Binds
    @Singleton
    abstract fun bindPlaybackController(impl: LocalPlaybackController): PlaybackController

    @Binds
    @Singleton
    abstract fun bindLanguageIdentifier(impl: MlKitLanguageIdentifier): LanguageIdentifier
}
