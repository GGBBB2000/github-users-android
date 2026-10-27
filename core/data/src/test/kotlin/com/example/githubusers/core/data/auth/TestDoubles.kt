package com.example.githubusers.core.data.auth

import com.example.githubusers.core.datastore.AuthSession
import com.example.githubusers.core.datastore.AuthSessionStore
import com.example.githubusers.core.network.GitHubApi
import com.example.githubusers.core.network.GitHubOAuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** メモリに保存する [AuthSessionStore]。 */
internal class InMemoryAuthSessionStore(initial: AuthSession? = null) : AuthSessionStore {
    override val session = MutableStateFlow(initial)
    var saveCount = 0
        private set

    override suspend fun save(session: AuthSession) {
        saveCount++
        this.session.value = session
    }

    override suspend fun clear() {
        session.value = null
    }
}

/** 時刻を進められる [Clock]。 */
internal class MutableClock(var now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}

/** [elapsedMillis] で現在時刻を決める [Clock]（runTest の仮想時間と合わせるため）。 */
internal class VirtualClock(private val start: Instant, private val elapsedMillis: () -> Long) : Clock() {
    override fun instant(): Instant = start.plusMillis(elapsedMillis())
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}

private val json = Json { ignoreUnknownKeys = true }

private fun MockWebServer.retrofit(): Retrofit = Retrofit.Builder()
    .baseUrl(url("/"))
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

internal fun MockWebServer.oauthApi(): GitHubOAuthApi = retrofit().create(GitHubOAuthApi::class.java)

internal fun MockWebServer.gitHubApi(): GitHubApi = retrofit().create(GitHubApi::class.java)

internal fun jsonResponse(body: String, code: Int = 200): MockResponse =
    MockResponse.Builder().code(code).body(body).build()

internal fun session(
    accessToken: String = "token-1",
    expiresAt: Instant? = null,
    refreshToken: String? = null,
    refreshExpiresAt: Instant? = null,
) = AuthSession(
    accessToken = accessToken,
    accessTokenExpiresAtEpochSecond = expiresAt?.epochSecond,
    refreshToken = refreshToken,
    refreshTokenExpiresAtEpochSecond = refreshExpiresAt?.epochSecond,
    login = "octocat",
    avatarUrl = "https://avatars.githubusercontent.com/u/583231",
)
