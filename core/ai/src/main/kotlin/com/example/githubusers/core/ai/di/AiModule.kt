package com.example.githubusers.core.ai.di

import com.example.githubusers.core.ai.ActivitySummarizer
import com.example.githubusers.core.ai.BuildConfig
import com.example.githubusers.core.ai.DefaultActivitySummarizer
import com.example.githubusers.core.ai.DemoTextGenerationEngine
import com.example.githubusers.core.ai.MlKitTextGenerationEngine
import com.example.githubusers.core.ai.TextGenerationEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider

@Module
@InstallIn(SingletonComponent::class)
internal interface AiModule {
    @Binds
    fun bindActivitySummarizer(impl: DefaultActivitySummarizer): ActivitySummarizer

    companion object {
        /** デモのビルドでは、ML Kit のクライアントを作らない */
        @Provides
        fun provideTextGenerationEngine(
            mlKit: Provider<MlKitTextGenerationEngine>,
            demo: Provider<DemoTextGenerationEngine>,
        ): TextGenerationEngine = if (BuildConfig.DEMO_ON_DEVICE_AI) demo.get() else mlKit.get()
    }
}
