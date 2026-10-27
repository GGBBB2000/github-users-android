package com.example.githubusers.core.model

/** `GET /users` の一覧に出てくるユーザー。 */
data class User(
    val id: Long,
    val login: String,
    val avatarUrl: String,
    val type: AccountType,
)

/** GitHub アカウントの種類。 */
enum class AccountType {
    User,
    Organization,
    Bot,
    Unknown,
    ;

    companion object {
        fun from(value: String?): AccountType = entries.firstOrNull { it.name == value } ?: Unknown
    }
}
