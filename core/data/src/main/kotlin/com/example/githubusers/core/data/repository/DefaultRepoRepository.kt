package com.example.githubusers.core.data.repository

import com.example.githubusers.core.data.apiCall
import com.example.githubusers.core.data.asModel
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.RepoSort
import com.example.githubusers.core.network.GitHubApi
import javax.inject.Inject

internal class DefaultRepoRepository @Inject constructor(
    private val api: GitHubApi,
) : RepoRepository {

    override suspend fun getRepos(login: String, sort: RepoSort, page: Int): List<Repo> = apiCall {
        when (sort) {
            RepoSort.Updated -> api.getRepos(login, "updated", "desc", RepoRepository.PAGE_SIZE, page)
            RepoSort.Name -> api.getRepos(login, "full_name", "asc", RepoRepository.PAGE_SIZE, page)
            // `/users/{login}/repos` はスター順に並べられないので Search API を使う
            RepoSort.Stars -> api.searchRepos(
                query = starsQuery(login),
                sort = "stars",
                order = "desc",
                perPage = RepoRepository.PAGE_SIZE,
                page = page,
            ).items
        }.map { it.asModel() }
    }

    override suspend fun getPopularRepos(login: String, limit: Int): List<Repo> = apiCall {
        api.searchRepos(
            query = starsQuery(login),
            sort = "stars",
            order = "desc",
            perPage = limit,
            page = 1,
        ).items.map { it.asModel() }
    }

    // Search API は既定でフォークを除くので、`/users/{login}/repos` と揃えるために含める
    private fun starsQuery(login: String) = "user:$login fork:true"
}
