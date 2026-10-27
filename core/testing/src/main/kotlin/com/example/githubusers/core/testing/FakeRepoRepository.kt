package com.example.githubusers.core.testing

import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.data.repository.RepoRepository
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.RepoSort

/**
 * テスト用の [RepoRepository]。並び替えは手元で行う。
 *
 * ViewModel は [RepoRepository.PAGE_SIZE] 件未満なら最後のページとみなすので、同じ件数ずつ返す。
 */
class FakeRepoRepository(
    var repos: List<Repo> = emptyList(),
) : RepoRepository {
    private val pageSize = RepoRepository.PAGE_SIZE

    var nextError: GitHubException? = null
    var popularError: GitHubException? = null
    val requests = mutableListOf<Pair<RepoSort, Int>>()

    override suspend fun getRepos(login: String, sort: RepoSort, page: Int): List<Repo> {
        requests += sort to page
        nextError?.let { nextError = null; throw it }
        val sorted = when (sort) {
            RepoSort.Updated -> repos.sortedByDescending { it.updatedAt }
            RepoSort.Stars -> repos.sortedByDescending { it.stargazersCount }
            RepoSort.Name -> repos.sortedBy { it.name.lowercase() }
        }
        return sorted.drop((page - 1) * pageSize).take(pageSize)
    }

    override suspend fun getPopularRepos(login: String, limit: Int): List<Repo> {
        popularError?.let { throw it }
        return repos.sortedByDescending { it.stargazersCount }.take(limit)
    }
}
