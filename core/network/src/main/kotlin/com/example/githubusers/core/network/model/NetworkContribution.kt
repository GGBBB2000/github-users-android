package com.example.githubusers.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** GraphQL API のリクエスト。 */
@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: JsonObject,
)

/** GraphQL API のエラー。`type` は `NOT_FOUND`、`RATE_LIMITED` など。 */
@Serializable
data class NetworkGraphQLError(
    val type: String? = null,
    val message: String? = null,
)

@Serializable
data class NetworkContributionResponse(
    val data: Data? = null,
    val errors: List<NetworkGraphQLError>? = null,
) {
    @Serializable
    data class Data(val user: User? = null)

    @Serializable
    data class User(val contributionsCollection: Collection)

    @Serializable
    data class Collection(val contributionCalendar: NetworkContributionCalendar)
}

@Serializable
data class NetworkContributionCalendar(
    val totalContributions: Int,
    val weeks: List<Week>,
) {
    @Serializable
    data class Week(val contributionDays: List<Day>)

    @Serializable
    data class Day(
        val date: String,
        val weekday: Int,
        val contributionCount: Int,
        val contributionLevel: String,
    )
}
