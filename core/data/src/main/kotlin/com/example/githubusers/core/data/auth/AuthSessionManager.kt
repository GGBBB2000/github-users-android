package com.example.githubusers.core.data.auth

import com.example.githubusers.core.datastore.AuthSession
import com.example.githubusers.core.datastore.AuthSessionStore
import com.example.githubusers.core.network.AuthTokenProvider
import com.example.githubusers.core.network.GitHubOAuthApi
import com.example.githubusers.core.network.GitHubOAuthConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * サインイン情報を持ち、API の呼び出しにトークンを渡す。
 *
 * - トークンはメモリにも持ち、通信のたびに DataStore を読まない。書き込みはすべてこのクラスを通す
 * - 期限付きのトークンは、期限の [REFRESH_MARGIN] 前になったら更新する
 * - 更新は [mutex] で1つにまとめる。リフレッシュトークンは使うたびに新しいものに変わるので、
 *   同時に2回更新すると片方が失敗するため
 */
@Singleton
internal class AuthSessionManager @Inject constructor(
    private val store: AuthSessionStore,
    private val oauthApi: GitHubOAuthApi,
    private val oauthConfig: GitHubOAuthConfig,
    private val clock: Clock,
) : AuthTokenProvider {

    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    @Volatile
    private var cached: AuthSession? = null

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** トークンが無効になって、自動でサインアウトしたとき。 */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    val session: Flow<AuthSession?> get() = store.session

    // OkHttp のスレッドから呼ばれる。ほとんどの場合はメモリの値を返すだけで、待たない
    override fun accessToken(): String? {
        if (loaded) {
            val session = cached ?: return null
            if (!session.needsRefresh()) return session.accessToken
        }
        return runBlocking { currentAccessToken() }
    }

    override fun onUnauthorized(rejectedToken: String): String? = runBlocking {
        mutex.withLock {
            val session = loadLocked() ?: return@withLock null
            // 待っている間に、ほかの通信が更新済み
            if (session.accessToken != rejectedToken) return@withLock session.accessToken
            when (val result = refreshLocked(session)) {
                is RefreshResult.Refreshed -> result.session.accessToken
                // 通信できずに更新できなかったときは、トークンなしでやり直す（サインインは続ける）
                RefreshResult.Unavailable -> null
                RefreshResult.Rejected -> {
                    expireLocked()
                    null
                }
            }
        }
    }

    suspend fun currentAccessToken(): String? = mutex.withLock {
        val session = loadLocked() ?: return@withLock null
        if (!session.needsRefresh()) return@withLock session.accessToken
        when (val result = refreshLocked(session)) {
            is RefreshResult.Refreshed -> result.session.accessToken
            // 期限まで少し残っているので、今のトークンを使う
            RefreshResult.Unavailable -> session.accessToken
            RefreshResult.Rejected -> {
                expireLocked()
                null
            }
        }
    }

    suspend fun signIn(session: AuthSession) = mutex.withLock {
        store.save(session)
        cached = session
        loaded = true
    }

    suspend fun signOut() = mutex.withLock {
        clearLocked()
    }

    private suspend fun loadLocked(): AuthSession? {
        if (!loaded) {
            cached = store.session.first()
            loaded = true
        }
        return cached
    }

    private suspend fun refreshLocked(session: AuthSession): RefreshResult {
        val refreshToken = session.refreshToken ?: return RefreshResult.Rejected
        val now = clock.instant()
        if (session.refreshTokenExpiresAt?.let { now >= it } == true) return RefreshResult.Rejected
        val response = try {
            oauthApi.refreshAccessToken(clientId = oauthConfig.clientId, refreshToken = refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return RefreshResult.Unavailable
        } catch (_: HttpException) {
            return RefreshResult.Unavailable
        } catch (_: SerializationException) {
            return RefreshResult.Unavailable
        }
        val accessToken = response.accessToken
        // bad_refresh_token など。リフレッシュトークンが取り消されたか、期限が切れている
        if (response.error != null || accessToken == null) return RefreshResult.Rejected
        val refreshed = session.copy(
            accessToken = accessToken,
            accessTokenExpiresAtEpochSecond = response.expiresIn?.let { now.plusSeconds(it).epochSecond },
            refreshToken = response.refreshToken,
            refreshTokenExpiresAtEpochSecond = response.refreshTokenExpiresIn?.let { now.plusSeconds(it).epochSecond },
        )
        store.save(refreshed)
        cached = refreshed
        return RefreshResult.Refreshed(refreshed)
    }

    private suspend fun expireLocked() {
        clearLocked()
        _sessionExpired.tryEmit(Unit)
    }

    private suspend fun clearLocked() {
        store.clear()
        cached = null
        loaded = true
    }

    private fun AuthSession.needsRefresh(): Boolean {
        val expiresAt = accessTokenExpiresAt ?: return false
        return refreshToken != null && clock.instant() >= expiresAt.minus(REFRESH_MARGIN)
    }

    private sealed interface RefreshResult {
        data class Refreshed(val session: AuthSession) : RefreshResult

        /** 通信できなかった。サインインは続ける */
        data object Unavailable : RefreshResult

        /** 更新を断られた。サインアウトする */
        data object Rejected : RefreshResult
    }

    companion object {
        val REFRESH_MARGIN: Duration = Duration.ofMinutes(5)
    }
}
