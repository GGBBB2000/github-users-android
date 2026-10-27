package com.example.githubusers.core.datastore

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * 保存しているサインイン情報。
 *
 * 期限なしのトークン（OAuth App など）では、期限とリフレッシュトークンが null になる。
 */
@Serializable
data class AuthSession(
    val accessToken: String,
    /** アクセストークンの有効期限（エポック秒） */
    val accessTokenExpiresAtEpochSecond: Long? = null,
    val refreshToken: String? = null,
    /** リフレッシュトークンの有効期限（エポック秒） */
    val refreshTokenExpiresAtEpochSecond: Long? = null,
    val login: String,
    val avatarUrl: String? = null,
) {
    val accessTokenExpiresAt: Instant? get() = accessTokenExpiresAtEpochSecond?.let(Instant::ofEpochSecond)
    val refreshTokenExpiresAt: Instant? get() = refreshTokenExpiresAtEpochSecond?.let(Instant::ofEpochSecond)

    // ログなどに出たときにトークンが漏れないようにする
    override fun toString(): String = "AuthSession(login=$login, accessTokenExpiresAt=$accessTokenExpiresAt)"
}

/** DataStore に保存する中身。 */
@Serializable
internal data class AuthData(
    val session: AuthSession? = null,
)
