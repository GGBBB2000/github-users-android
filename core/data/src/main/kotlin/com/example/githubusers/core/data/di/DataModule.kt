package com.example.githubusers.core.data.di

import com.example.githubusers.core.data.repository.DefaultRepoRepository
import com.example.githubusers.core.data.repository.DefaultUserRepository
import com.example.githubusers.core.data.repository.RepoRepository
import com.example.githubusers.core.data.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun bindUserRepository(impl: DefaultUserRepository): UserRepository

    @Binds
    fun bindRepoRepository(impl: DefaultRepoRepository): RepoRepository
}
