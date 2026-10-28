package com.example.githubusers.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** 画面を閉じても続ける処理（AI のモデルのダウンロードなど）に使う、アプリ全体のスコープ。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
internal object CoroutineScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
