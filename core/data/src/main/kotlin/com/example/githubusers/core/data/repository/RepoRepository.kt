package com.example.githubusers.core.data.repository

import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.RepoSort

/** ユーザーの公開リポジトリを取得する。失敗すると [com.example.githubusers.core.model.GitHubException] を投げる。 */
interface RepoRepository {
    /**
     * @param page 1 から始まるページ番号
     */
    suspend fun getRepos(login: String, sort: RepoSort, page: Int): List<Repo>

    /** スター数の多い順に上位 [limit] 件を取得する。 */
    suspend fun getPopularRepos(login: String, limit: Int = POPULAR_LIMIT): List<Repo>

    companion object {
        const val PAGE_SIZE = 30
        const val POPULAR_LIMIT = 3
    }
}
