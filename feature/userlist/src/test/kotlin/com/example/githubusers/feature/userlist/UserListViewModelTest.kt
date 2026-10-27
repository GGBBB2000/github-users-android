package com.example.githubusers.feature.userlist

import androidx.lifecycle.SavedStateHandle
import com.example.githubusers.core.model.AuthState
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.testing.FakeAuthRepository
import com.example.githubusers.core.testing.FakeUserRepository
import com.example.githubusers.core.testing.MainDispatcherRule
import com.example.githubusers.core.testing.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class UserListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeUserRepository(users = TestData.users(1L..25L), pageSize = 10)

    private val authRepository = FakeAuthRepository()

    private fun createViewModel() = UserListViewModel(repository, authRepository, SavedStateHandle())

    /** uiState は購読している間だけ更新されるので、購読してから最新の値を返す。 */
    private suspend fun UserListViewModel.state(): UserListUiState = uiState.first()

    @Test
    fun `最初のページを読み込む`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        val state = viewModel.state()
        assertEquals(InitialLoad.Loaded, state.initialLoad)
        assertEquals((1L..10L).toList(), state.users.map { it.id })
        assertEquals(listOf<Long?>(null), repository.requestedSince)
    }

    @Test
    fun `読み込み中はスケルトンの状態になる`() = runTest {
        repository.gate = CompletableDeferred()
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        assertEquals(InitialLoad.Loading, viewModel.state().initialLoad)
        repository.gate!!.complete(Unit)
        assertEquals(InitialLoad.Loaded, viewModel.state().initialLoad)
    }

    @Test
    fun `続きは最後のユーザーの ID を since に渡して読み込む`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.loadMore()
        viewModel.loadMore()
        viewModel.loadMore()

        val state = viewModel.state()
        assertEquals((1L..25L).toList(), state.users.map { it.id })
        assertEquals(listOf(null, 10L, 20L, 25L), repository.requestedSince)
        assertEquals(AppendState.EndReached, state.append)
    }

    @Test
    fun `読み込み済みの一覧をログイン名で絞り込む`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onQueryChange("USER1")

        val state = viewModel.state()
        assertEquals(listOf("user1", "user10"), state.users.map { it.login })
        assertEquals(10, state.loadedCount)
        assertTrue(state.isFiltering)
    }

    @Test
    fun `初回の読み込みに失敗したら再試行できる`() = runTest {
        repository.nextError = GitHubException.Network(IOException())
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        assertTrue(viewModel.state().initialLoad is InitialLoad.Failed)

        viewModel.retry()
        assertEquals(InitialLoad.Loaded, viewModel.state().initialLoad)
    }

    @Test
    fun `追加読み込みがレート制限で失敗したら Snackbar を出し、再試行で続きを読み込む`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        val rateLimited = GitHubException.RateLimited(resetAt = null)
        repository.nextError = rateLimited

        viewModel.loadMore()

        var state = viewModel.state()
        assertEquals(AppendState.Failed(rateLimited), state.append)
        assertEquals(rateLimited, state.rateLimitNotice)

        viewModel.onRateLimitNoticeShown()
        viewModel.retry()

        state = viewModel.state()
        assertNull(state.rateLimitNotice)
        assertEquals(AppendState.Idle, state.append)
        assertEquals(20, state.users.size)
    }

    @Test
    fun `更新に失敗しても表示中の一覧は残す`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        repository.nextError = GitHubException.Network(IOException())

        viewModel.refresh()

        val state = viewModel.state()
        assertEquals(InitialLoad.Loaded, state.initialLoad)
        assertEquals(10, state.users.size)
        assertEquals(false, state.isRefreshing)
    }

    @Test
    fun `未ログインでサインインの設定があれば、サインインの入口を出す`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        assertTrue(viewModel.state().canSignIn)

        authRepository.signIn("octocat")
        val state = viewModel.state()
        assertEquals(AuthState.SignedIn("octocat", null), state.authState)
        assertFalse(state.canSignIn)
    }

    @Test
    fun `Client ID が設定されていなければ、サインインの入口を出さない`() = runTest {
        authRepository.isSignInAvailable = false
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        assertFalse(viewModel.state().canSignIn)
    }

    @Test
    fun `初回の読み込みがレート制限で失敗した後にサインインしたら、読み込み直す`() = runTest {
        repository.nextError = GitHubException.RateLimited(resetAt = null)
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        assertTrue(viewModel.state().initialLoad is InitialLoad.Failed)

        authRepository.signIn()

        assertEquals(InitialLoad.Loaded, viewModel.state().initialLoad)
        assertEquals(listOf<Long?>(null, null), repository.requestedSince)
    }

    @Test
    fun `通信エラーで失敗しているときは、サインインしても読み込み直さない`() = runTest {
        repository.nextError = GitHubException.Network(IOException())
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        authRepository.signIn()

        assertTrue(viewModel.state().initialLoad is InitialLoad.Failed)
        assertEquals(1, repository.requestedSince.size)
    }

    @Test
    fun `サインアウトする`() = runTest {
        authRepository.signIn()
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.signOut()

        assertEquals(1, authRepository.signOutCount)
        assertEquals(AuthState.SignedOut, viewModel.state().authState)
    }
}
