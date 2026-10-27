package com.example.githubusers.core.network

import com.example.githubusers.core.network.model.NetworkRepo
import com.example.githubusers.core.network.model.NetworkRepoSearchResult
import com.example.githubusers.core.network.model.NetworkUser
import com.example.githubusers.core.network.model.NetworkUserDetail
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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

    companion object {
        const val BASE_URL = "https://api.github.com/"
    }
}
