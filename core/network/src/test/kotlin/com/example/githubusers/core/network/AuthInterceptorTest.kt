package com.example.githubusers.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
    private val server = MockWebServer()
    private val tokenProvider = FakeTokenProvider()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** MockWebServer のホストを API のホストとみなす。 */
    private fun client(apiHost: String = server.hostName) = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenProvider, apiHost))
        .authenticator(TokenAuthenticator(tokenProvider, apiHost))
        .build()

    private fun OkHttpClient.get(build: Request.Builder.() -> Unit = {}) =
        newCall(Request.Builder().url(server.url("/users")).apply(build).build()).execute().use { it.code }

    @Test
    fun `サインインしていればトークンを付ける`() {
        tokenProvider.token = "token-1"
        server.enqueue(MockResponse())

        client().get()

        assertEquals("Bearer token-1", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `サインインしていなければ何も付けない`() {
        server.enqueue(MockResponse())

        client().get()

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `API 以外のホストにはトークンを付けない`() {
        tokenProvider.token = "token-1"
        server.enqueue(MockResponse())

        client(apiHost = "api.github.com").get()

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `呼び出し側が付けたトークンは置き換えない`() {
        tokenProvider.token = "token-1"
        server.enqueue(MockResponse())

        client().get {
            header("Authorization", "Bearer new-token")
            tag(ExplicitAuthorization::class.java, ExplicitAuthorization)
        }

        assertEquals("Bearer new-token", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `401 なら新しいトークンでやり直す`() {
        tokenProvider.token = "token-1"
        tokenProvider.refreshedToken = "token-2"
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse())

        val code = client().get()

        assertEquals(200, code)
        assertEquals("Bearer token-1", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer token-2", server.takeRequest().headers["Authorization"])
        assertEquals(listOf("token-1"), tokenProvider.rejected)
    }

    @Test
    fun `更新できずにサインアウトしたら、トークンなしでやり直す`() {
        tokenProvider.token = "token-1"
        tokenProvider.refreshedToken = null
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse())

        val code = client().get()

        assertEquals(200, code)
        server.takeRequest()
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `やり直しは1回まで`() {
        tokenProvider.token = "token-1"
        tokenProvider.refreshedToken = "token-2"
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse())

        val code = client().get()

        assertEquals(401, code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `トークンなしで 401 が返ったときは何もしない`() {
        server.enqueue(MockResponse.Builder().code(401).build())

        assertEquals(401, client().get())
        assertEquals(emptyList<String>(), tokenProvider.rejected)
    }

    private class FakeTokenProvider : AuthTokenProvider {
        var token: String? = null
        var refreshedToken: String? = null
        val rejected = mutableListOf<String>()

        override fun accessToken(): String? = token

        override fun onUnauthorized(rejectedToken: String): String? {
            rejected += rejectedToken
            token = refreshedToken
            return refreshedToken
        }
    }
}
