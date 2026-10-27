package com.example.githubusers.core.data.repository

import com.example.githubusers.core.data.apiCall
import com.example.githubusers.core.data.auth.AuthSessionManager
import com.example.githubusers.core.datastore.AuthSession
import com.example.githubusers.core.model.AuthState
import com.example.githubusers.core.model.DeviceAuthorizationResult
import com.example.githubusers.core.model.DeviceCode
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.network.GitHubApi
import com.example.githubusers.core.network.GitHubOAuthApi
import com.example.githubusers.core.network.GitHubOAuthConfig
import com.example.githubusers.core.network.model.NetworkAccessToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Cache
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

internal class DefaultAuthRepository @Inject constructor(
    private val oauthApi: GitHubOAuthApi,
    private val api: GitHubApi,
    private val oauthConfig: GitHubOAuthConfig,
    private val sessionManager: AuthSessionManager,
    private val httpCache: Cache,
    private val clock: Clock,
) : AuthRepository {

    override val isSignInAvailable: Boolean get() = oauthConfig.isConfigured

    override val authState: Flow<AuthState> = sessionManager.session
        .map { session ->
            if (session == null) AuthState.SignedOut else AuthState.SignedIn(session.login, session.avatarUrl)
        }
        .distinctUntilChanged()

    override val sessionExpired: Flow<Unit> get() = sessionManager.sessionExpired

    override suspend fun requestDeviceCode(): DeviceCode {
        if (!oauthConfig.isConfigured) throw GitHubException.OAuth("client_id_missing")
        val response = apiCall { oauthApi.requestDeviceCode(clientId = oauthConfig.clientId) }
        response.error?.let { throw GitHubException.OAuth(it) }
        val deviceCode = response.deviceCode
        val userCode = response.userCode
        val verificationUri = response.verificationUri
        val expiresIn = response.expiresIn
        if (deviceCode == null || userCode == null || verificationUri == null || expiresIn == null) {
            throw GitHubException.Unexpected(IllegalStateException("Incomplete device code response"))
        }
        return DeviceCode(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = verificationUri,
            expiresAt = clock.instant().plusSeconds(expiresIn),
            intervalSeconds = response.interval ?: DEFAULT_INTERVAL_SECONDS,
        )
    }

    override suspend fun awaitAuthorization(deviceCode: DeviceCode): DeviceAuthorizationResult {
        var interval = deviceCode.intervalSeconds.coerceAtLeast(1)
        while (true) {
            delay(interval.seconds)
            if (clock.instant() >= deviceCode.expiresAt) return DeviceAuthorizationResult.Expired
            val response = try {
                apiCall {
                    oauthApi.pollAccessToken(clientId = oauthConfig.clientId, deviceCode = deviceCode.deviceCode)
                }
            } catch (e: GitHubException) {
                if (e.isTransient()) continue else throw e
            }
            when (val error = response.error) {
                null -> return DeviceAuthorizationResult.Authorized(completeSignIn(response))
                "authorization_pending" -> Unit
                // 間隔が短すぎる。新しい間隔が返るので、以降はそれを使う
                "slow_down" -> interval = response.interval ?: (interval + SLOW_DOWN_STEP_SECONDS)
                "expired_token" -> return DeviceAuthorizationResult.Expired
                "access_denied" -> return DeviceAuthorizationResult.Denied
                else -> throw GitHubException.OAuth(error)
            }
        }
    }

    override suspend fun signOut() {
        sessionManager.signOut()
        // 本人のデータ（今後 /user などを使ったとき）を端末に残さない
        withContext(Dispatchers.IO) {
            try {
                httpCache.evictAll()
            } catch (_: IOException) {
                // 消せなくてもサインアウトは完了している
            }
        }
    }

    /** 受け取ったトークンでユーザーを確かめてから保存する。 */
    private suspend fun completeSignIn(response: NetworkAccessToken): String {
        val accessToken = response.accessToken
            ?: throw GitHubException.Unexpected(IllegalStateException("No access token in response"))
        val user = apiCall { api.getAuthenticatedUser(authorization = "Bearer $accessToken") }
        val now = clock.instant()
        sessionManager.signIn(
            AuthSession(
                accessToken = accessToken,
                accessTokenExpiresAtEpochSecond = response.expiresIn?.let { now.plusSeconds(it).epochSecond },
                refreshToken = response.refreshToken,
                refreshTokenExpiresAtEpochSecond = response.refreshTokenExpiresIn?.let { now.plusSeconds(it).epochSecond },
                login = user.login,
                avatarUrl = user.avatarUrl,
            ),
        )
        return user.login
    }

    /** 問い合わせを続けてよいエラーか（通信できない、GitHub 側の一時的な障害）。 */
    private fun GitHubException.isTransient(): Boolean =
        this is GitHubException.Network || (this is GitHubException.Http && code >= 500)

    private companion object {
        const val DEFAULT_INTERVAL_SECONDS = 5L
        const val SLOW_DOWN_STEP_SECONDS = 5L
    }
}
