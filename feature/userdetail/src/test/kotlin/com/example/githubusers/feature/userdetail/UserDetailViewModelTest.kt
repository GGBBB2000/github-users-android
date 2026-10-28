package com.example.githubusers.feature.userdetail

import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.model.RepoSort
import com.example.githubusers.core.model.AccountType
import com.example.githubusers.core.testing.FakeActivitySummaryRepository
import com.example.githubusers.core.testing.FakeAuthRepository
import com.example.githubusers.core.testing.FakeContributionRepository
import com.example.githubusers.core.testing.FakeRepoRepository
import com.example.githubusers.core.testing.FakeUserRepository
import com.example.githubusers.core.testing.MainDispatcherRule
import com.example.githubusers.core.testing.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class UserDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = TestData.userDetail("mojombo")
    private val userRepository = FakeUserRepository(details = mapOf(user.login to user))
    // 1 ページ 30 件（RepoRepository.PAGE_SIZE）なので、3 ページ目の途中で終わる件数にする
    private val repoRepository = FakeRepoRepository(
        repos = (1L..65L).map { TestData.repo(it, name = "repo$it", stars = it.toInt() * 10) },
    )

    private val authRepository = FakeAuthRepository()
    private val contributionRepository = FakeContributionRepository()
    private val summaryRepository = FakeActivitySummaryRepository()

    private fun createViewModel() = UserDetailViewModel(
        "mojombo",
        userRepository,
        repoRepository,
        contributionRepository,
        authRepository,
        summaryRepository,
    )

    @Test
    fun `プロフィール・人気のリポジトリ・リポジトリ一覧を読み込む`() = runTest {
        val state = createViewModel().uiState.value

        assertEquals(ProfileState.Success(user), state.profile)
        assertEquals(
            listOf("repo65", "repo64", "repo63"),
            (state.popularRepos as PopularReposState.Success).repos.map { it.name },
        )
        assertEquals(30, state.repos.repos.size)
        assertTrue(state.repos.canLoadMore)
    }

    @Test
    fun `プロフィールの取得に失敗したら再試行できる`() = runTest {
        userRepository.nextError = GitHubException.Network(IOException())
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.profile is ProfileState.Failed)

        viewModel.retry()
        assertEquals(ProfileState.Success(user), viewModel.uiState.value.profile)
    }

    @Test
    fun `人気のリポジトリが取れなくてもプロフィールは表示する`() = runTest {
        repoRepository.popularError = GitHubException.RateLimited(resetAt = null)
        val state = createViewModel().uiState.value

        assertEquals(PopularReposState.Unavailable, state.popularRepos)
        assertEquals(ProfileState.Success(user), state.profile)
    }

    @Test
    fun `さらに表示で次のページを追加し、最後のページで止まる`() = runTest {
        val viewModel = createViewModel()
        viewModel.loadMoreRepos()
        viewModel.loadMoreRepos()

        val repos = viewModel.uiState.value.repos
        assertEquals(65, repos.repos.size)
        assertFalse(repos.canLoadMore)
        assertEquals(listOf(RepoSort.Updated to 1, RepoSort.Updated to 2, RepoSort.Updated to 3), repoRepository.requests)
    }

    @Test
    fun `並び替えると 1 ページ目から読み込み直す`() = runTest {
        val viewModel = createViewModel()
        viewModel.loadMoreRepos()

        viewModel.onSortChange(RepoSort.Stars)

        val repos = viewModel.uiState.value.repos
        assertEquals(RepoSort.Stars, repos.sort)
        assertEquals(listOf("repo65", "repo64"), repos.repos.take(2).map { it.name })
        assertEquals(RepoSort.Stars to 1, repoRepository.requests.last())
    }

    @Test
    fun `リポジトリ一覧の読み込みに失敗したら、失敗したページから再試行する`() = runTest {
        val viewModel = createViewModel()
        repoRepository.nextError = GitHubException.Network(IOException())

        viewModel.loadMoreRepos()
        assertTrue(viewModel.uiState.value.repos.error != null)

        viewModel.retryRepos()
        val repos = viewModel.uiState.value.repos
        assertEquals(60, repos.repos.size)
        assertEquals(RepoSort.Updated to 2, repoRepository.requests.last())
    }

    @Test
    fun `プロフィールがレート制限で取れなかった後にサインインしたら、すべて読み込み直す`() = runTest {
        userRepository.nextError = GitHubException.RateLimited(resetAt = null)
        repoRepository.popularError = GitHubException.RateLimited(resetAt = null)
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.canSignIn)
        assertTrue(viewModel.uiState.value.profile is ProfileState.Failed)

        repoRepository.popularError = null
        authRepository.signIn()

        val state = viewModel.uiState.value
        assertEquals(ProfileState.Success(user), state.profile)
        assertTrue(state.popularRepos is PopularReposState.Success)
        assertFalse(state.canSignIn)
    }

    @Test
    fun `人気のリポジトリだけ取れなかったときは、サインインしたらそれだけ読み込み直す`() = runTest {
        repoRepository.popularError = GitHubException.RateLimited(resetAt = null)
        val viewModel = createViewModel()
        assertEquals(PopularReposState.Unavailable, viewModel.uiState.value.popularRepos)
        val requestsBefore = repoRepository.requests.size

        repoRepository.popularError = null
        authRepository.signIn()

        assertTrue(viewModel.uiState.value.popularRepos is PopularReposState.Success)
        assertEquals(requestsBefore, repoRepository.requests.size)
    }

    @Test
    fun `未ログインなら草の代わりにサインインを勧める`() = runTest {
        val viewModel = createViewModel()

        assertEquals(ContributionState.SignedOut, viewModel.uiState.value.contributions)
        assertTrue(contributionRepository.requests.isEmpty())
    }

    @Test
    fun `サインインしていれば草を読み込む`() = runTest {
        authRepository.signIn()
        val viewModel = createViewModel()

        assertEquals(
            ContributionState.Success(contributionRepository.calendar),
            viewModel.uiState.value.contributions,
        )
        assertEquals(listOf("mojombo"), contributionRepository.requests)
    }

    @Test
    fun `サインインしたら草を読み込み、サインアウトしたらサインインを勧める表示に戻す`() = runTest {
        val viewModel = createViewModel()

        authRepository.signIn()
        assertTrue(viewModel.uiState.value.contributions is ContributionState.Success)

        authRepository.signOut()
        assertEquals(ContributionState.SignedOut, viewModel.uiState.value.contributions)
    }

    @Test
    fun `草の読み込みに失敗したら再試行できる`() = runTest {
        authRepository.signIn()
        val error = GitHubException.Network(IOException())
        contributionRepository.nextError = error
        val viewModel = createViewModel()
        assertEquals(ContributionState.Failed(error), viewModel.uiState.value.contributions)

        viewModel.retryContributions()

        assertTrue(viewModel.uiState.value.contributions is ContributionState.Success)
    }

    @Test
    fun `Organization には草を出さない`() = runTest {
        userRepository.details = mapOf(user.login to user.copy(type = AccountType.Organization))
        val viewModel = createViewModel()

        assertEquals(ContributionState.Hidden, viewModel.uiState.value.contributions)

        // サインインしても問い合わせない
        authRepository.signIn()
        assertEquals(ContributionState.Hidden, viewModel.uiState.value.contributions)
        assertTrue(contributionRepository.requests.isEmpty())
    }

    @Test
    fun `GraphQL がユーザーを見つけられなければ草を出さない`() = runTest {
        authRepository.signIn()
        contributionRepository.nextError = GitHubException.NotFound()
        val viewModel = createViewModel()

        assertEquals(ContributionState.Hidden, viewModel.uiState.value.contributions)
    }

    @Test
    fun `サインインの設定がなければ草のセクションを出さない`() = runTest {
        authRepository.isSignInAvailable = false
        val viewModel = createViewModel()

        assertEquals(ContributionState.Hidden, viewModel.uiState.value.contributions)
    }
}
