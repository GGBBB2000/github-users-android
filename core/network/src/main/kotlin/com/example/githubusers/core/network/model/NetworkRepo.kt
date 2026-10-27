package com.example.githubusers.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkRepo(
    val id: Long,
    val name: String,
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    @SerialName("updated_at") val updatedAt: String,
    val fork: Boolean = false,
)

@Serializable
data class NetworkRepoSearchResult(
    @SerialName("total_count") val totalCount: Int,
    val items: List<NetworkRepo>,
)
