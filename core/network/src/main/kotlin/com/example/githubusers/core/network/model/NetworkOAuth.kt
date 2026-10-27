package com.example.githubusers.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /login/device/code` のレスポンス。失敗したときは [error] だけが入る。 */
@Serializable
data class NetworkDeviceCode(
    @SerialName("device_code") val deviceCode: String? = null,
    @SerialName("user_code") val userCode: String? = null,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val interval: Long? = null,
    val error: String? = null,
)

/**
 * `POST /login/oauth/access_token` のレスポンス。
 *
 * 成功したときは [accessToken] が入る。期限付きのトークンなら [expiresIn] と [refreshToken] も入る。
 * 失敗したときは [error] が入る（`slow_down` のときは新しい [interval] も入る）。
 */
@Serializable
data class NetworkAccessToken(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("refresh_token_expires_in") val refreshTokenExpiresIn: Long? = null,
    val interval: Long? = null,
    val error: String? = null,
)
