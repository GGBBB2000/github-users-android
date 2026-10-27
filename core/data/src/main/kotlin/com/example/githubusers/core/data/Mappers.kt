package com.example.githubusers.core.data

import com.example.githubusers.core.model.AccountType
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.User
import com.example.githubusers.core.model.UserDetail
import com.example.githubusers.core.network.model.NetworkRepo
import com.example.githubusers.core.network.model.NetworkUser
import com.example.githubusers.core.network.model.NetworkUserDetail
import java.time.Instant

internal fun NetworkUser.asModel() = User(
    id = id,
    login = login,
    avatarUrl = avatarUrl,
    type = AccountType.from(type),
)

internal fun NetworkUserDetail.asModel() = UserDetail(
    id = id,
    login = login,
    avatarUrl = avatarUrl,
    htmlUrl = htmlUrl,
    type = AccountType.from(type),
    name = name.nullIfBlank(),
    company = company.nullIfBlank(),
    blog = blog.nullIfBlank(),
    location = location.nullIfBlank(),
    email = email.nullIfBlank(),
    bio = bio.nullIfBlank(),
    twitterUsername = twitterUsername.nullIfBlank(),
    publicRepos = publicRepos,
    publicGists = publicGists,
    followers = followers,
    following = following,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
)

internal fun NetworkRepo.asModel() = Repo(
    id = id,
    name = name,
    description = description.nullIfBlank(),
    htmlUrl = htmlUrl,
    language = language.nullIfBlank(),
    stargazersCount = stargazersCount,
    forksCount = forksCount,
    updatedAt = Instant.parse(updatedAt),
    isFork = fork,
)

// GitHub は未設定の項目を null ではなく空文字で返すことがある（blog など）
private fun String?.nullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
