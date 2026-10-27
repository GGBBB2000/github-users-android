package com.example.githubusers.core.data.repository

import com.example.githubusers.core.model.AuthState
import com.example.githubusers.core.model.DeviceAuthorizationResult
import com.example.githubusers.core.model.DeviceCode
import kotlinx.coroutines.flow.Flow

/**
 * GitHub へのサインイン（Device Flow）。
 *
 * 失敗すると [com.example.githubusers.core.model.GitHubException] を投げる。
 */
interface AuthRepository {
    /** GitHub App の Client ID が設定されていて、サインインできるか。 */
    val isSignInAvailable: Boolean

    val authState: Flow<AuthState>

    /** GitHub 側で許可が取り消されるなどしてトークンが無効になり、自動でサインアウトしたとき。 */
    val sessionExpired: Flow<Unit>

    /** ユーザーに入力してもらうコードを発行する。 */
    suspend fun requestDeviceCode(): DeviceCode

    /**
     * ユーザーが GitHub のページで許可するのを待つ。許可されたらトークンを保存する。
     *
     * 一時的な通信エラーでは止めずに、次の間隔で問い合わせ直す。
     */
    suspend fun awaitAuthorization(deviceCode: DeviceCode): DeviceAuthorizationResult

    /** この端末のトークンと HTTP キャッシュを消す。GitHub 側の許可は取り消せない（`client_secret` が必要なため）。 */
    suspend fun signOut()
}
