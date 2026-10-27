package com.example.githubusers.core.data.auth

import com.example.githubusers.core.data.repository.DefaultAuthRepository
import com.example.githubusers.core.model.AuthState
import com.example.githubusers.core.model.DeviceAuthorizationResult
import com.example.githubusers.core.model.DeviceCode
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.network.GitHubOAuthConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import okhttp3.Cache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class DefaultAuthRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val start = Instant.parse("2026-10-28T00:00:00Z")
    private val store = InMemoryAuthSessionStore()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** 時刻は runTest の仮想時間に合わせる（問い合わせの間隔と有効期限を確かめるため）。 */
    private fun TestScope.createRepository(clientId: String = "client-id"): DefaultAuthRepository {
        val clock = VirtualClock(start) { testScheduler.currentTime }
        val config = GitHubOAuthConfig(clientId)
        val oauthApi = server.oauthApi()
        return DefaultAuthRepository(
            oauthApi = oauthApi,
            api = server.gitHubApi(),
            oauthConfig = config,
            sessionManager = AuthSessionManager(store, oauthApi, config, clock),
            httpCache = Cache(tempFolder.newFolder("http_cache"), 1024 * 1024),
            clock = clock,
        )
    }

    private fun deviceCode(expiresAt: Instant = start.plusSeconds(900), interval: Long = 5) = DeviceCode(
        deviceCode = "device-1",
        userCode = "WDJB-MJHT",
        verificationUri = "https://github.com/login/device",
        expiresAt = expiresAt,
        intervalSeconds = interval,
    )

    @Test
    fun `コードを発行する`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"device_code":"device-1","user_code":"WDJB-MJHT","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
            ),
        )

        val code = createRepository().requestDeviceCode()

        assertEquals(deviceCode(), code)
        val request = server.takeRequest()
        assertEquals("/login/device/code", request.target)
        assertEquals("client_id=client-id", request.body?.utf8())
    }

    @Test
    fun `コードの発行が断られたら OAuth のエラーにする`() = runTest {
        server.enqueue(jsonResponse("""{"error":"device_flow_disabled"}"""))

        try {
            createRepository().requestDeviceCode()
            fail()
        } catch (e: GitHubException.OAuth) {
            assertEquals("device_flow_disabled", e.error)
        }
    }

    @Test
    fun `許可されるまで間隔を守って問い合わせ、許可されたらトークンを保存する`() = runTest {
        server.enqueue(jsonResponse("""{"error":"authorization_pending"}"""))
        // 間隔が短すぎると言われたら、返ってきた間隔に延ばす
        server.enqueue(jsonResponse("""{"error":"slow_down","interval":10}"""))
        server.enqueue(
            jsonResponse(
                """{"access_token":"token-1","expires_in":28800,"refresh_token":"refresh-1","refresh_token_expires_in":15897600}""",
            ),
        )
        server.enqueue(jsonResponse(USER_JSON))
        val repository = createRepository()

        val result = repository.awaitAuthorization(deviceCode())

        assertEquals(DeviceAuthorizationResult.Authorized("octocat"), result)
        // 5 秒、5 秒、（slow_down の後）10 秒
        assertEquals(20_000L, testScheduler.currentTime)
        val poll = server.takeRequest()
        assertEquals(
            "client_id=client-id&device_code=device-1&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code",
            poll.body?.utf8(),
        )
        server.takeRequest()
        server.takeRequest()
        val user = server.takeRequest()
        assertEquals("/user", user.target)
        assertEquals("Bearer token-1", user.headers["Authorization"])

        val saved = store.session.value!!
        assertEquals("token-1", saved.accessToken)
        assertEquals("refresh-1", saved.refreshToken)
        assertEquals(start.plusSeconds(20 + 28800), saved.accessTokenExpiresAt)
        assertEquals(AuthState.SignedIn("octocat", "https://avatars.githubusercontent.com/u/583231"), repository.authState.first())
    }

    @Test
    fun `期限切れと拒否を返す`() = runTest {
        server.enqueue(jsonResponse("""{"error":"expired_token"}"""))
        server.enqueue(jsonResponse("""{"error":"access_denied"}"""))
        val repository = createRepository()

        assertEquals(DeviceAuthorizationResult.Expired, repository.awaitAuthorization(deviceCode()))
        assertEquals(DeviceAuthorizationResult.Denied, repository.awaitAuthorization(deviceCode()))
    }

    @Test
    fun `有効期限を過ぎたら、問い合わせずに期限切れを返す`() = runTest {
        val result = createRepository().awaitAuthorization(deviceCode(expiresAt = start.plusSeconds(3)))

        assertEquals(DeviceAuthorizationResult.Expired, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `GitHub 側の一時的な障害では止めずに問い合わせ直す`() = runTest {
        server.enqueue(jsonResponse("""{"message":"Server Error"}""", code = 502))
        server.enqueue(jsonResponse("""{"access_token":"token-1"}"""))
        server.enqueue(jsonResponse(USER_JSON))

        val result = createRepository().awaitAuthorization(deviceCode())

        assertEquals(DeviceAuthorizationResult.Authorized("octocat"), result)
        // 期限なしのトークン（OAuth App など）も保存できる
        assertEquals(null, store.session.value?.accessTokenExpiresAt)
    }

    @Test
    fun `想定していないエラーでは問い合わせをやめる`() = runTest {
        server.enqueue(jsonResponse("""{"error":"incorrect_client_credentials"}"""))

        try {
            createRepository().awaitAuthorization(deviceCode())
            fail()
        } catch (e: GitHubException.OAuth) {
            assertEquals("incorrect_client_credentials", e.error)
        }
    }

    @Test
    fun `サインアウトするとトークンを消す`() = runTest {
        store.save(session())
        val repository = createRepository()
        assertTrue(repository.authState.first() is AuthState.SignedIn)

        repository.signOut()

        assertEquals(AuthState.SignedOut, repository.authState.first())
    }

    @Test
    fun `Client ID がなければサインインできない`() = runTest {
        val repository = createRepository(clientId = "")

        assertEquals(false, repository.isSignInAvailable)
        try {
            repository.requestDeviceCode()
            fail()
        } catch (_: GitHubException.OAuth) {
        }
        assertEquals(0, server.requestCount)
    }

    private companion object {
        const val USER_JSON = """{"id":583231,"login":"octocat","avatar_url":"https://avatars.githubusercontent.com/u/583231","html_url":"https://github.com/octocat","type":"User","created_at":"2011-01-25T18:44:36Z","updated_at":"2026-01-01T00:00:00Z"}"""
    }
}
