package com.example.githubusers.core.model

import java.time.Instant

/** ユーザーが所有する公開リポジトリ。 */
data class Repo(
    val id: Long,
    val name: String,
    val description: String?,
    val htmlUrl: String,
    val language: String?,
    val stargazersCount: Int,
    val forksCount: Int,
    val updatedAt: Instant,
    val isFork: Boolean,
)

/** リポジトリ一覧の並び順。 */
enum class RepoSort {
    /** 更新順（新しい順） */
    Updated,

    /** スター順（多い順） */
    Stars,

    /** 名前順 */
    Name,
}
