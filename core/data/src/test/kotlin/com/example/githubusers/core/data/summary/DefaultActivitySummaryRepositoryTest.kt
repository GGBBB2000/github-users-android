package com.example.githubusers.core.data.summary

import com.example.githubusers.core.ai.ActivitySummarizer
import com.example.githubusers.core.data.repository.DefaultActivitySummaryRepository
import com.example.githubusers.core.model.AccountType
import com.example.githubusers.core.model.ContributionCalendar
import com.example.githubusers.core.model.SummaryException
import com.example.githubusers.core.model.SummaryFact
import com.example.githubusers.core.model.SummaryModelState
import com.example.githubusers.core.model.UserDetail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultActivitySummaryRepositoryTest {
    private val summarizer = FakeSummarizer()
    private val clock = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC)

    private val user = UserDetail(
        id = 1, login = "mojombo", avatarUrl = "", htmlUrl = "", type = AccountType.User,
        name = null, company = null, blog = null, location = null, email = null, bio = null, twitterUsername = null,
        publicRepos = 66, publicGists = 0, followers = 24_130, following = 0,
        createdAt = Instant.parse("2007-10-20T05:24:19Z"), updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    /** ダウンロードはアプリ全体のスコープで動くので、backgroundScope を渡す（進めるときは runCurrent を使う） */
    private fun TestScope.repository() = DefaultActivitySummaryRepository(summarizer, clock, backgroundScope)

    @Test
    fun `端末の状態を確かめる`() = runTest {
        val repository = repository()
        assertEquals(SummaryModelState.Checking, repository.modelState.value)

        summarizer.status = SummaryModelState.Downloadable
        repository.refreshModelState()
        runCurrent()

        assertEquals(SummaryModelState.Downloadable, repository.modelState.value)
    }

    @Test
    fun `ダウンロードの進み具合を流し、終わったら使える状態にする`() = runTest {
        val repository = repository()
        val finish = CompletableDeferred<Unit>()
        summarizer.download = flow {
            emit(SummaryModelState.Downloading(0, 1_000))
            emit(SummaryModelState.Downloading(400, 1_000))
            finish.await()
        }

        repository.downloadModel()
        runCurrent()
        assertEquals(SummaryModelState.Downloading(400, 1_000), repository.modelState.value)
        // ダウンロード中に状態を確かめ直しても、上書きしない
        summarizer.status = SummaryModelState.Downloadable
        repository.refreshModelState()
        runCurrent()
        assertEquals(SummaryModelState.Downloading(400, 1_000), repository.modelState.value)

        finish.complete(Unit)
        runCurrent()
        assertEquals(SummaryModelState.Available, repository.modelState.value)
        assertEquals(1, summarizer.downloadCount)
    }

    @Test
    fun `システムがダウンロード中なら、その進み具合を追う`() = runTest {
        val repository = repository()
        summarizer.status = SummaryModelState.Downloading(0, null)

        repository.refreshModelState()
        runCurrent()

        assertEquals(1, summarizer.downloadCount)
        assertEquals(SummaryModelState.Available, repository.modelState.value)
    }

    @Test
    fun `ダウンロードに失敗したら、失敗の状態にする`() = runTest {
        val repository = repository()
        summarizer.download = flow { throw SummaryException.DownloadFailed() }

        repository.downloadModel()
        runCurrent()

        assertEquals(SummaryModelState.DownloadFailed, repository.modelState.value)
    }

    @Test
    fun `作った要約をログイン名ごとに覚える`() = runTest {
        val repository = repository()
        assertNull(repository.summary("mojombo").first())

        val summary = repository.summarize(ActivitySummaryInput(user, calendar = null, repos = emptyList(), popularRepos = emptyList()))

        assertEquals(summary, repository.summary("mojombo").first())
        assertEquals("要約", summary.text)
        assertFalse(summary.includesContributions)
        // 名前がなければログイン名を渡す
        assertEquals("mojombo", summarizer.names.single())
        assertEquals(summary.facts, summarizer.facts.single())
        assertNull(repository.summary("other").first())
    }

    @Test
    fun `草を含めて作ったかを覚える`() = runTest {
        val calendar = ContributionCalendar(totalContributions = 0, weeks = emptyList())

        val summary = repository().summarize(ActivitySummaryInput(user, calendar, emptyList(), emptyList()))

        assertTrue(summary.includesContributions)
    }

    @Test
    fun `失敗したら覚えない`() = runTest {
        val repository = repository()
        summarizer.error = SummaryException.Busy()

        runCatching { repository.summarize(ActivitySummaryInput(user, null, emptyList(), emptyList())) }

        assertNull(repository.summary("mojombo").first())
    }

    private class FakeSummarizer : ActivitySummarizer {
        var status: SummaryModelState = SummaryModelState.Available
        var download: Flow<SummaryModelState.Downloading> = flow {}
        var error: SummaryException? = null
        var downloadCount = 0
        val names = mutableListOf<String>()
        val facts = mutableListOf<List<SummaryFact>>()

        override suspend fun checkStatus() = status

        override fun download(): Flow<SummaryModelState.Downloading> {
            downloadCount++
            return download
        }

        override suspend fun summarize(name: String, facts: List<SummaryFact>): String {
            error?.let { throw it }
            names += name
            this.facts += facts
            return "要約"
        }
    }
}
