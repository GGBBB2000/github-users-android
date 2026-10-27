package com.example.githubusers.core.data.repository

import com.example.githubusers.core.data.apiCall
import com.example.githubusers.core.data.asModel
import com.example.githubusers.core.model.User
import com.example.githubusers.core.model.UserDetail
import com.example.githubusers.core.network.GitHubApi
import javax.inject.Inject

internal class DefaultUserRepository @Inject constructor(
    private val api: GitHubApi,
) : UserRepository {

    override suspend fun getUsers(since: Long?): List<User> = apiCall {
        api.getUsers(since = since, perPage = UserRepository.PAGE_SIZE).map { it.asModel() }
    }

    override suspend fun getUserDetail(login: String): UserDetail = apiCall {
        api.getUser(login).asModel()
    }
}
