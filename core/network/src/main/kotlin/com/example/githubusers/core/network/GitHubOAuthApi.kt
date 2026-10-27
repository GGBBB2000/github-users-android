package com.example.githubusers.core.network

import com.example.githubusers.core.network.model.NetworkAccessToken
import com.example.githubusers.core.network.model.NetworkDeviceCode
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * GitHub の OAuth（Device Flow）の API。`api.github.com` ではなく `github.com` にある。
 *
 * 失敗しても HTTP 200 で返り、本文の `error` で区別するものがある（`authorization_pending` など）。
 * `client_secret` は使わない（Device Flow では不要）。
 *
 * @see <a href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app#using-the-device-flow-to-generate-a-user-access-token">Using the device flow</a>
 */
interface GitHubOAuthApi {
    @FormUrlEncoded
    @POST("login/device/code")
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
    ): NetworkDeviceCode

    @FormUrlEncoded
    @POST("login/oauth/access_token")
    suspend fun pollAccessToken(
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = GRANT_TYPE_DEVICE_CODE,
    ): NetworkAccessToken

    /** 期限付きのトークンを更新する。Device Flow で取ったトークンは `client_secret` なしで更新できる。 */
    @FormUrlEncoded
    @POST("login/oauth/access_token")
    suspend fun refreshAccessToken(
        @Field("client_id") clientId: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = GRANT_TYPE_REFRESH_TOKEN,
    ): NetworkAccessToken

    companion object {
        const val BASE_URL = "https://github.com/"
        const val GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code"
        const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"
    }
}

/** GitHub App の設定。 */
data class GitHubOAuthConfig(
    /** 空ならサインインできない（入口を出さない） */
    val clientId: String,
) {
    val isConfigured: Boolean get() = clientId.isNotBlank()
}
