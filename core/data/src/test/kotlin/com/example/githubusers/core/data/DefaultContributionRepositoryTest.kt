package com.example.githubusers.core.data

import com.example.githubusers.core.data.auth.gitHubApi
import com.example.githubusers.core.data.auth.jsonResponse
import com.example.githubusers.core.data.repository.DefaultContributionRepository
import com.example.githubusers.core.model.ContributionLevel
import com.example.githubusers.core.model.GitHubException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class DefaultContributionRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: DefaultContributionRepository

    @Before
    fun setUp() {
        server.start()
        repository = DefaultContributionRepository(server.gitHubApi())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `カレンダーを取得し、ログイン名を変数で渡す`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {"data":{"user":{"contributionsCollection":{"contributionCalendar":{
                  "totalContributions":7,
                  "weeks":[{"contributionDays":[
                    {"date":"2026-10-25","weekday":0,"contributionCount":5,"contributionLevel":"FOURTH_QUARTILE"},
                    {"date":"2026-10-26","weekday":1,"contributionCount":2,"contributionLevel":"FIRST_QUARTILE"},
                    {"date":"2026-10-27","weekday":2,"contributionCount":0,"contributionLevel":"NONE"}
                  ]}]
                }}}}}
                """.trimIndent(),
            ),
        )

        val calendar = repository.getContributionCalendar("mojombo")

        assertEquals(7, calendar.totalContributions)
        val days = calendar.days
        assertEquals(LocalDate.parse("2026-10-25"), days[0].date)
        assertEquals(ContributionLevel.FourthQuartile, days[0].level)
        assertEquals(ContributionLevel.None, days[2].level)
        assertEquals(days[0], calendar.busiestDay)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/graphql", request.target)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("mojombo", body["variables"]!!.jsonObject["login"]!!.jsonPrimitive.content)
        assertTrue(body["query"]!!.jsonPrimitive.content.contains("contributionCalendar"))
    }

    @Test
    fun `ユーザーが見つからなければ NotFound にする（Organization など）`() = runTest {
        server.enqueue(
            jsonResponse("""{"data":{"user":null},"errors":[{"type":"NOT_FOUND","message":"Could not resolve to a User"}]}"""),
        )

        try {
            repository.getContributionCalendar("github")
            fail()
        } catch (_: GitHubException.NotFound) {
        }
    }

    @Test
    fun `GraphQL のレート制限は RateLimited にする`() = runTest {
        server.enqueue(jsonResponse("""{"errors":[{"type":"RATE_LIMITED","message":"API rate limit exceeded"}]}"""))

        try {
            repository.getContributionCalendar("mojombo")
            fail()
        } catch (_: GitHubException.RateLimited) {
        }
    }
}
