package com.example.githubusers.core.testing

import com.example.githubusers.core.model.AccountType
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.User
import com.example.githubusers.core.model.UserDetail
import java.time.Instant

/** テストとプレビューで使うデータ。 */
object TestData {
    fun user(id: Long, login: String = "user$id") = User(
        id = id,
        login = login,
        avatarUrl = "https://avatars.githubusercontent.com/u/$id?v=4",
        type = AccountType.User,
    )

    fun users(range: LongRange): List<User> = range.map { user(it) }

    fun userDetail(login: String = "mojombo") = UserDetail(
        id = 1,
        login = login,
        avatarUrl = "https://avatars.githubusercontent.com/u/1?v=4",
        htmlUrl = "https://github.com/$login",
        type = AccountType.User,
        name = "Tom Preston-Werner",
        company = null,
        blog = "http://tom.preston-werner.com",
        location = "San Francisco",
        email = null,
        bio = null,
        twitterUsername = "mojombo",
        publicRepos = 66,
        publicGists = 62,
        followers = 24_100,
        following = 11,
        createdAt = Instant.parse("2007-10-20T05:24:19Z"),
        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    fun repo(id: Long, name: String = "repo$id", stars: Int = 0) = Repo(
        id = id,
        name = name,
        description = "Description of $name",
        htmlUrl = "https://github.com/mojombo/$name",
        language = "Ruby",
        stargazersCount = stars,
        forksCount = 0,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        isFork = false,
    )
}
