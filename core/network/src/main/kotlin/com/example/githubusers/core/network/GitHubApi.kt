package com.example.githubusers.core.network

import com.example.githubusers.core.network.model.GraphQLRequest
import com.example.githubusers.core.network.model.NetworkContributionResponse
import com.example.githubusers.core.network.model.NetworkRepo
import com.example.githubusers.core.network.model.NetworkRepoSearchResult
import com.example.githubusers.core.network.model.NetworkUser
import com.example.githubusers.core.network.model.NetworkUserDetail
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

/**
 * GitHub REST API の定義。
 *
 * エラー時（2xx 以外）は [retrofit2.HttpException] を投げる。
 */
interface GitHubApi {
    @GET("users")
    suspend fun getUsers(
        @Query("since") since: Long?,
        @Query("per_page") perPage: Int,
    ): List<NetworkUser>

    @GET("users/{login}")
    suspend fun getUser(@Path("login") login: String): NetworkUserDetail

    /**
     * [authorization] のトークンの持ち主。サインインの直後、トークンを保存する前に呼ぶ。
     *
     * @param authorization `Bearer {token}`
     */
    @GET("user")
    suspend fun getAuthenticatedUser(
        @Header("Authorization") authorization: String,
        @Tag tag: ExplicitAuthorization = ExplicitAuthorization,
    ): NetworkUserDetail

    /**
     * @param sort `updated`、`full_name` など。スター順は API が対応していない
     */
    @GET("users/{login}/repos")
    suspend fun getRepos(
        @Path("login") login: String,
        @Query("sort") sort: String,
        @Query("direction") direction: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<NetworkRepo>

    /** Search API。`/users/{login}/repos` はスター順に並べられないので、人気のリポジトリはこちらで取る。 */
    @GET("search/repositories")
    suspend fun searchRepos(
        @Query("q") query: String,
        @Query("sort") sort: String,
        @Query("order") order: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): NetworkRepoSearchResult

    /**
     * コントリビューションのカレンダー（草）。GraphQL API は**サインインが必要**。
     *
     * GraphQL のエラー（ユーザーが見つからないなど）は HTTP 200 で返り、本文の `errors` に入る。
     */
    @POST("graphql")
    suspend fun getContributions(@Body request: GraphQLRequest): NetworkContributionResponse

    companion object {
        const val BASE_URL = "https://api.github.com/"

        /** 期間を指定しなければ、過去1年間になる。 */
        const val CONTRIBUTIONS_QUERY = """
            query(${'$'}login: String!) {
              user(login: ${'$'}login) {
                contributionsCollection {
                  contributionCalendar {
                    totalContributions
                    weeks { contributionDays { date weekday contributionCount contributionLevel } }
                  }
                }
              }
            }
        """
    }
}
