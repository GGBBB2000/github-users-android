package com.example.githubusers.core.model

import java.time.Instant

/** `GET /users/{login}` で取得するユーザーの詳細。 */
data class UserDetail(
    val id: Long,
    val login: String,
    val avatarUrl: String,
    val htmlUrl: String,
    val type: AccountType,
    val name: String?,
    val company: String?,
    val blog: String?,
    val location: String?,
    val email: String?,
    val bio: String?,
    val twitterUsername: String?,
    val publicRepos: Int,
    val publicGists: Int,
    val followers: Int,
    val following: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** 開閉エリアに出すプロフィール項目が1つでもあるか。 */
    val hasProfileDetails: Boolean
        get() = listOf(company, location, blog, email, twitterUsername).any { !it.isNullOrBlank() }
}
