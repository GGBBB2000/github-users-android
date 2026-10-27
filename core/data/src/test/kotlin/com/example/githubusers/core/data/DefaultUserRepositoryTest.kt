package com.example.githubusers.core.data

import com.example.githubusers.core.data.repository.DefaultUserRepository
import com.example.githubusers.core.model.AccountType
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.network.GitHubApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant

class DefaultUserRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: DefaultUserRepository

    @Before
    fun setUp() {
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
        repository = DefaultUserRepository(api)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `ユーザー一覧を取得し、since と per_page を送る`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .body("""[{"id":1,"login":"mojombo","avatar_url":"https://a/1","type":"User","site_admin":false}]""")
                .build(),
        )

        val users = repository.getUsers(since = 46)

        assertEquals("mojombo", users.single().login)
        assertEquals(AccountType.User, users.single().type)
        val path = server.takeRequest().target
        assertTrue(path, path.contains("since=46") && path.contains("per_page=100"))
    }

    @Test
    fun `空文字の項目は null として扱う`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .body(
                    """
                    {"id":1,"login":"mojombo","avatar_url":"a","html_url":"h","type":"User",
                     "name":"Tom","blog":"","bio":null,"public_repos":1,"public_gists":2,
                     "followers":3,"following":4,
                     "created_at":"2007-10-20T05:24:19Z","updated_at":"2026-01-01T00:00:00Z"}
                    """.trimIndent(),
                )
                .build(),
        )

        val user = repository.getUserDetail("mojombo")

        assertNull(user.blog)
        assertEquals(Instant.parse("2007-10-20T05:24:19Z"), user.createdAt)
    }

    @Test
    fun `残り回数が 0 の 403 はレート制限として扱い、解除時刻を読み取る`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(403)
                .addHeader("x-ratelimit-remaining", "0")
                .addHeader("x-ratelimit-reset", "1800000000")
                .body("""{"message":"API rate limit exceeded"}""")
                .build(),
        )

        try {
            repository.getUsers(since = null)
            fail("例外が投げられるはず")
        } catch (e: GitHubException.RateLimited) {
            assertEquals(Instant.ofEpochSecond(1_800_000_000), e.resetAt)
        }
    }

    @Test
    fun `残り回数がある 403 はレート制限として扱わない`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).addHeader("x-ratelimit-remaining", "10").build())

        try {
            repository.getUsers(since = null)
            fail("例外が投げられるはず")
        } catch (e: GitHubException.Http) {
            assertEquals(403, e.code)
        }
    }

    @Test
    fun `404 は NotFound にする`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())

        try {
            repository.getUserDetail("nobody")
            fail("例外が投げられるはず")
        } catch (_: GitHubException.NotFound) {
        }
    }
}
