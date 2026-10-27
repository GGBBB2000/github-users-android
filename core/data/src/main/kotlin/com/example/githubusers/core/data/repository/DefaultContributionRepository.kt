package com.example.githubusers.core.data.repository

import com.example.githubusers.core.data.apiCall
import com.example.githubusers.core.data.asModel
import com.example.githubusers.core.model.ContributionCalendar
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.network.GitHubApi
import com.example.githubusers.core.network.model.GraphQLRequest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

internal class DefaultContributionRepository @Inject constructor(
    private val api: GitHubApi,
) : ContributionRepository {

    override suspend fun getContributionCalendar(login: String): ContributionCalendar = apiCall {
        val response = api.getContributions(
            GraphQLRequest(
                query = GitHubApi.CONTRIBUTIONS_QUERY,
                variables = buildJsonObject { put("login", JsonPrimitive(login)) },
            ),
        )
        // GraphQL のエラーは HTTP 200 で返る
        response.errors?.firstOrNull()?.let { error ->
            throw when (error.type) {
                "NOT_FOUND" -> GitHubException.NotFound()
                "RATE_LIMITED" -> GitHubException.RateLimited(resetAt = null)
                else -> GitHubException.Unexpected(IllegalStateException("GraphQL error: ${error.type}"))
            }
        }
        val calendar = response.data?.user?.contributionsCollection?.contributionCalendar
            ?: throw GitHubException.NotFound()
        calendar.asModel()
    }
}
