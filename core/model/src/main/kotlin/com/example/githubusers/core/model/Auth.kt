package com.example.githubusers.core.model

import java.time.Instant

/** サインインの状態。 */
sealed interface AuthState {
    data object SignedOut : AuthState

    data class SignedIn(val login: String, val avatarUrl: String?) : AuthState
}

/**
 * Device Flow で GitHub から受け取るコード。
 *
 * [userCode] をユーザーに見せ、[verificationUri] で入力してもらう。
 * [deviceCode] はトークンを問い合わせるときに使う（画面には出さない）。
 */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresAt: Instant,
    /** 問い合わせの最短の間隔（秒） */
    val intervalSeconds: Long,
)

/** Device Flow でユーザーの操作を待った結果。 */
sealed interface DeviceAuthorizationResult {
    /** 許可された。トークンは保存済み。 */
    data class Authorized(val login: String) : DeviceAuthorizationResult

    /** 許可される前にコードの有効期限が切れた。 */
    data object Expired : DeviceAuthorizationResult

    /** ユーザーが GitHub のページで拒否した。 */
    data object Denied : DeviceAuthorizationResult
}
