package com.example.githubusers.core.data.auth

import com.example.githubusers.core.network.GitHubOAuthConfig
import com.example.githubusers.core.datastore.AuthSession
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.Executors

class AuthSessionManagerTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-10-28T00:00:00Z")
    private val clock = MutableClock(now)
    private lateinit var store: InMemoryAuthSessionStore

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun createManager(initial: AuthSession?): AuthSessionManager {
        store = InMemoryAuthSessionStore(initial)
        return AuthSessionManager(store, server.oauthApi(), GitHubOAuthConfig("client-id"), clock)
    }

    @Test
    fun `サインインしていなければトークンは null`() {
        assertNull(createManager(null).accessToken())
    }

    @Test
    fun `期限まで余裕があれば、保存しているトークンをそのまま返す`() {
        val manager = createManager(session(expiresAt = now.plusSeconds(3600), refreshToken = "refresh-1"))

        assertEquals("token-1", manager.accessToken())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `期限が近ければ、更新してから新しいトークンを返す`() {
        val manager = createManager(session(expiresAt = now.plusSeconds(60), refreshToken = "refresh-1"))
        server.enqueue(
            jsonResponse(
                """{"access_token":"token-2","expires_in":28800,"refresh_token":"refresh-2","refresh_token_expires_in":15897600}""",
            ),
        )

        assertEquals("token-2", manager.accessToken())

        val request = server.takeRequest()
        assertEquals("/login/oauth/access_token", request.target)
        assertEquals(
            "client_id=client-id&refresh_token=refresh-1&grant_type=refresh_token",
            request.body?.utf8(),
        )
        val saved = store.session.value!!
        assertEquals("refresh-2", saved.refreshToken)
        assertEquals(now.plusSeconds(28800), saved.accessTokenExpiresAt)
    }

    @Test
    fun `期限が近くても、通信できなければ今のトークンを使う`() {
        val manager = createManager(session(expiresAt = now.plusSeconds(60), refreshToken = "refresh-1"))
        // 接続できない状態にする
        server.close()

        assertEquals("token-1", manager.accessToken())
        assertEquals("token-1", store.session.value?.accessToken)
    }

    @Test
    fun `401 のときは更新して、新しいトークンでやり直す`() {
        val manager = createManager(session(expiresAt = now.plusSeconds(3600), refreshToken = "refresh-1"))
        server.enqueue(jsonResponse("""{"access_token":"token-2","expires_in":28800,"refresh_token":"refresh-2"}"""))

        assertEquals("token-2", manager.onUnauthorized("token-1"))
    }

    @Test
    fun `更新を断られたらサインアウトして、トークンなしでやり直す`() = runTest {
        val manager = createManager(session(expiresAt = now.plusSeconds(3600), refreshToken = "refresh-1"))
        server.enqueue(jsonResponse("""{"error":"bad_refresh_token"}"""))
        val expired = async { withTimeout(5_000) { manager.sessionExpired.first() } }
        testScheduler.runCurrent()

        assertNull(manager.onUnauthorized("token-1"))

        expired.await()
        assertNull(store.session.value)
        assertNull(manager.accessToken())
    }

    @Test
    fun `期限なしのトークンで 401 が返ったら、更新せずにサインアウトする`() {
        val manager = createManager(session())

        assertNull(manager.onUnauthorized("token-1"))

        assertEquals(0, server.requestCount)
        assertNull(store.session.value)
    }

    @Test
    fun `リフレッシュトークンの期限が切れていたら、更新せずにサインアウトする`() {
        val manager = createManager(
            session(expiresAt = now.minusSeconds(10), refreshToken = "refresh-1", refreshExpiresAt = now.minusSeconds(1)),
        )

        assertNull(manager.accessToken())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `ほかの通信が更新済みなら、更新せずに新しいトークンを返す`() {
        val manager = createManager(session(expiresAt = now.plusSeconds(3600), refreshToken = "refresh-1"))
        server.enqueue(jsonResponse("""{"access_token":"token-2","expires_in":28800,"refresh_token":"refresh-2"}"""))
        assertEquals("token-2", manager.onUnauthorized("token-1"))

        // 古いトークンで送っていた別の通信も 401 になる
        assertEquals("token-2", manager.onUnauthorized("token-1"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `同時に 401 が返っても、更新は1回だけ行う`() {
        val manager = createManager(session(expiresAt = now.plusSeconds(3600), refreshToken = "refresh-1"))
        server.enqueue(jsonResponse("""{"access_token":"token-2","expires_in":28800,"refresh_token":"refresh-2"}"""))
        // 2回目の更新が起きたら失敗するようにしておく
        server.enqueue(jsonResponse("""{"error":"bad_refresh_token"}"""))

        // OkHttp のスレッドから同時に呼ばれる状況を、別々のスレッドで再現する
        val executor = Executors.newFixedThreadPool(4)
        val tokens = runBlocking(executor.asCoroutineDispatcher()) {
            (1..4).map { async { manager.onUnauthorized("token-1") } }.awaitAll()
        }
        executor.shutdown()

        assertEquals(List(4) { "token-2" }, tokens)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `サインインとサインアウトはメモリと保存先の両方に反映する`() = runTest {
        val manager = createManager(null)

        manager.signIn(session(accessToken = "token-9"))
        assertEquals("token-9", manager.accessToken())
        assertEquals("token-9", store.session.value?.accessToken)

        manager.signOut()
        assertNull(manager.accessToken())
        assertNull(store.session.value)
    }
}
