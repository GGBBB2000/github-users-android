package com.example.githubusers.feature.userdetail

import com.example.githubusers.core.model.SummaryException
import com.example.githubusers.core.model.SummaryModelState
import com.example.githubusers.core.testing.FakeActivitySummaryRepository
import com.example.githubusers.core.testing.FakeAuthRepository
import com.example.githubusers.core.testing.FakeContributionRepository
import com.example.githubusers.core.testing.FakeRepoRepository
import com.example.githubusers.core.testing.FakeUserRepository
import com.example.githubusers.core.testing.MainDispatcherRule
import com.example.githubusers.core.testing.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** ユーザー詳細の「活動の要約」（端末上の AI）。 */
class UserDetailViewModelSummaryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var user = TestData.userDetail("mojombo")
    private val repoRepository = FakeRepoRepository(
        repos = (1L..40L).map { TestData.repo(it, stars = it.toInt()) },
    )
    private val authRepository = FakeAuthRepository()
    private val contributionRepository = FakeContributionRepository(
        calendar = TestData.contributionCalendar { if (it.dayOfMonth % 2 == 0) 3 else 0 },
    )
    private val summaryRepository = FakeActivitySummaryRepository()

    private fun createViewModel() = UserDetailViewModel(
        "mojombo",
        FakeUserRepository(details = mapOf(user.login to user)),
        repoRepository,
        contributionRepository,
        authRepository,
        summaryRepository,
    )

    private val UserDetailViewModel.summary get() = uiState.value.summary

    @Test
    fun `端末が対応していなければ、要約に関するものを何も出さない`() = runTest {
        summaryRepository.modelState.value = SummaryModelState.Unavailable
        val viewModel = createViewModel()

        assertEquals(SummaryState.Hidden, viewModel.summary)
        assertEquals(1, summaryRepository.refreshCount)
    }

    @Test
    fun `状態を確かめている間も何も出さない`() = runTest {
        summaryRepository.modelState.value = SummaryModelState.Checking

        assertEquals(SummaryState.Hidden, createViewModel().summary)
    }

    @Test
    fun `押すまでは作らず、押すと読み込み済みのデータで要約を作る`() = runTest {
        val viewModel = createViewModel()
        assertEquals(SummaryState.Entry, viewModel.summary)
        assertTrue(summaryRepository.inputs.isEmpty())

        viewModel.generateSummary()

        val done = viewModel.summary as SummaryState.Done
        assertEquals("要約 1", done.summary.text)
        assertFalse(done.canIncludeContributions)
        val input = summaryRepository.inputs.single()
        assertEquals(user, input.user)
        // 未ログインなので草はない
        assertNull(input.calendar)
        assertEquals(30, input.repos.size)
        assertEquals(3, input.popularRepos.size)
    }

    @Test
    fun `サインインしていれば草も含めて作る`() = runTest {
        authRepository.signIn()
        val viewModel = createViewModel()

        viewModel.generateSummary()

        assertNotNull(summaryRepository.inputs.single().calendar)
    }

    @Test
    fun `作っている間は作成中を出し、中止すると入口に戻る`() = runTest {
        summaryRepository.gate = CompletableDeferred()
        val viewModel = createViewModel()

        viewModel.generateSummary()
        assertEquals(SummaryState.Generating, viewModel.summary)
        // 作っている間にもう一度押しても、2回は頼まない
        viewModel.generateSummary()
        assertEquals(1, summaryRepository.inputs.size)

        viewModel.cancelSummary()
        assertEquals(SummaryState.Entry, viewModel.summary)
    }

    @Test
    fun `作った要約は、詳細を開き直しても押さずに出す`() = runTest {
        createViewModel().generateSummary()

        val reopened = createViewModel()

        assertEquals("要約 1", (reopened.summary as SummaryState.Done).summary.text)
        assertEquals(1, summaryRepository.inputs.size)
    }

    @Test
    fun `失敗の種類ごとに表示を変える`() = runTest {
        val cases = listOf(
            SummaryException.Busy() to SummaryFailure.Busy,
            SummaryException.QuotaExceeded() to SummaryFailure.QuotaExceeded,
            SummaryException.InvalidOutput("unknown numbers") to SummaryFailure.Other,
            SummaryException.Failed() to SummaryFailure.Other,
        )
        cases.forEach { (error, expected) ->
            summaryRepository.nextError = error
            val viewModel = createViewModel()

            viewModel.generateSummary()

            assertEquals(SummaryState.Failed(expected), viewModel.summary)
        }
    }

    @Test
    fun `失敗の後に再試行できる`() = runTest {
        summaryRepository.nextError = SummaryException.Busy()
        val viewModel = createViewModel()
        viewModel.generateSummary()

        viewModel.generateSummary()

        assertTrue(viewModel.summary is SummaryState.Done)
    }

    @Test
    fun `アプリが前面でなくなって止まったときは、中止と同じに入口に戻す`() = runTest {
        summaryRepository.nextError = SummaryException.Interrupted()
        val viewModel = createViewModel()

        viewModel.generateSummary()

        assertEquals(SummaryState.Entry, viewModel.summary)
    }

    @Test
    fun `要約がある状態でもう一度作るのに失敗したら、要約は残して知らせる`() = runTest {
        val viewModel = createViewModel()
        val messages = mutableListOf<SummaryFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.summaryMessages.collect { messages += it } }
        viewModel.generateSummary()

        summaryRepository.nextError = SummaryException.QuotaExceeded()
        viewModel.generateSummary()

        assertEquals("要約 1", (viewModel.summary as SummaryState.Done).summary.text)
        assertEquals(listOf(SummaryFailure.QuotaExceeded), messages)
    }

    @Test
    fun `草なしで作った後にサインインしたら、草を含めて作り直せることを示す`() = runTest {
        val viewModel = createViewModel()
        viewModel.generateSummary()

        authRepository.signIn()

        assertTrue((viewModel.summary as SummaryState.Done).canIncludeContributions)
        viewModel.generateSummary()
        val done = viewModel.summary as SummaryState.Done
        assertEquals("要約 2", done.summary.text)
        assertFalse(done.canIncludeContributions)
    }

    @Test
    fun `公開リポジトリがなく草も少なければ、要約できないことを示す`() = runTest {
        user = user.copy(publicRepos = 0)
        authRepository.signIn()
        contributionRepository.calendar = TestData.contributionCalendar { if (it == LocalDate.parse("2026-10-01")) 9 else 0 }

        assertEquals(SummaryState.NoData, createViewModel().summary)
    }

    @Test
    fun `公開リポジトリがなくても、草が多ければ要約できる`() = runTest {
        user = user.copy(publicRepos = 0)
        authRepository.signIn()

        assertEquals(SummaryState.Entry, createViewModel().summary)
    }

    @Test
    fun `モデルがなければダウンロードを勧め、押すとダウンロードする`() = runTest {
        summaryRepository.modelState.value = SummaryModelState.Downloadable
        val viewModel = createViewModel()
        assertEquals(SummaryState.NeedsDownload, viewModel.summary)

        viewModel.downloadSummaryModel()

        assertEquals(1, summaryRepository.downloadCount)
        assertEquals(SummaryState.Downloading(downloadedBytes = 0, totalBytes = null), viewModel.summary)

        summaryRepository.modelState.value = SummaryModelState.Available
        assertEquals(SummaryState.Entry, viewModel.summary)
    }
}
