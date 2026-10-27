package com.example.githubusers.core.testing

import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.data.repository.UserRepository
import com.example.githubusers.core.model.User
import com.example.githubusers.core.model.UserDetail
import kotlinx.coroutines.CompletableDeferred

/**
 * テスト用の [UserRepository]。
 *
 * [users] を ID 順に [pageSize] 件ずつ返す。[nextError] を設定すると、次の1回だけその例外を投げる。
 * [gate] を設定すると、完了されるまで応答を止める（読み込み中の状態を確かめるため）。
 */
class FakeUserRepository(
    var users: List<User> = emptyList(),
    var details: Map<String, UserDetail> = emptyMap(),
    private val pageSize: Int = UserRepository.PAGE_SIZE,
) : UserRepository {
    var nextError: GitHubException? = null
    var gate: CompletableDeferred<Unit>? = null
    val requestedSince = mutableListOf<Long?>()

    override suspend fun getUsers(since: Long?): List<User> {
        requestedSince += since
        gate?.await()
        nextError?.let { nextError = null; throw it }
        return users.filter { since == null || it.id > since }.take(pageSize)
    }

    override suspend fun getUserDetail(login: String): UserDetail {
        gate?.await()
        nextError?.let { nextError = null; throw it }
        return details[login] ?: throw GitHubException.NotFound()
    }
}
