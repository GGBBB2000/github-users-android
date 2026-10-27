package com.example.githubusers.core.data.di

import com.example.githubusers.core.data.auth.AuthSessionManager
import com.example.githubusers.core.data.repository.AuthRepository
import com.example.githubusers.core.data.repository.DefaultAuthRepository
import com.example.githubusers.core.data.repository.DefaultRepoRepository
import com.example.githubusers.core.data.repository.DefaultUserRepository
import com.example.githubusers.core.data.repository.RepoRepository
import com.example.githubusers.core.data.repository.UserRepository
import com.example.githubusers.core.network.AuthTokenProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun bindUserRepository(impl: DefaultUserRepository): UserRepository

    @Binds
    fun bindRepoRepository(impl: DefaultRepoRepository): RepoRepository

    @Binds
    fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository

    /** `:core:network` の OkHttpClient がトークンを受け取る先。 */
    @Binds
    fun bindAuthTokenProvider(impl: AuthSessionManager): AuthTokenProvider

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemUTC()
    }
}
