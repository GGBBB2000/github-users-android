package com.example.githubusers.core.data.repository

import com.example.githubusers.core.model.User
import com.example.githubusers.core.model.UserDetail

/** GitHub のユーザーを取得する。失敗すると [com.example.githubusers.core.model.GitHubException] を投げる。 */
interface UserRepository {
    /**
     * ユーザー一覧を ID の昇順で取得する。
     *
     * @param since この ID より後のユーザーを返す。最初のページは null
     */
    suspend fun getUsers(since: Long?): List<User>

    suspend fun getUserDetail(login: String): UserDetail

    companion object {
        /** 一覧の1ページの件数。レート制限が厳しいので API の上限（100）にする。 */
        const val PAGE_SIZE = 100
    }
}
