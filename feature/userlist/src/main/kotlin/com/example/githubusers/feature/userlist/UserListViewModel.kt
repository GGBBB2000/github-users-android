package com.example.githubusers.feature.userlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubusers.core.data.repository.AuthRepository
import com.example.githubusers.core.data.repository.UserRepository
import com.example.githubusers.core.model.AuthState
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ユーザー一覧。
 *
 * `/users` には検索機能がないので、読み込み済みのユーザーを手元で絞り込む。
 * 追加の読み込みは、最後に読み込んだユーザーの ID を `since` に渡して行う。
 * レート制限で失敗した後にサインインしたら、失敗した読み込みをやり直す。
 */
@HiltViewModel
class UserListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val pages = MutableStateFlow(PagesState())
    private val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private var loadJob: Job? = null

    val uiState: StateFlow<UserListUiState> = combine(pages, query, authState) { pages, query, authState ->
        UserListUiState(
            query = query,
            users = pages.users.filterBy(query),
            loadedCount = pages.users.size,
            initialLoad = pages.initialLoad,
            append = pages.append,
            isRefreshing = pages.isRefreshing,
            rateLimitNotice = pages.rateLimitNotice,
            authState = authState,
            isSignInAvailable = authRepository.isSignInAvailable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserListUiState())

    init {
        loadFirstPage(refreshing = false)
        viewModelScope.launch {
            var previous: AuthState? = null
            authRepository.authState.collect { state ->
                authState.value = state
                if (previous is AuthState.SignedOut && state is AuthState.SignedIn) onSignedIn()
                previous = state
            }
        }
    }

    /** サインアウトする。終わるのを待たずに Snackbar を出してよい（端末内の処理だけなので）。 */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /** レート制限で失敗していた読み込みを、サインインした後にやり直す。 */
    private fun onSignedIn() {
        val current = pages.value
        val initialError = (current.initialLoad as? InitialLoad.Failed)?.error
        val appendError = (current.append as? AppendState.Failed)?.error
        if (initialError is GitHubException.RateLimited || appendError is GitHubException.RateLimited) retry()
    }

    fun onQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
    }

    /** 一覧の末尾に近づいたとき、または「さらに読み込む」で呼ぶ。 */
    fun loadMore() {
        val current = pages.value
        if (current.initialLoad != InitialLoad.Loaded || current.append != AppendState.Idle) return
        if (loadJob?.isActive == true) return
        loadNextPage()
    }

    /** 失敗した読み込みをやり直す。 */
    fun retry() {
        when {
            pages.value.initialLoad is InitialLoad.Failed -> loadFirstPage(refreshing = false)
            pages.value.append is AppendState.Failed -> loadNextPage()
        }
    }

    /** 引っ張って更新。最初のページから読み込み直す。 */
    fun refresh() {
        loadFirstPage(refreshing = true)
    }

    fun onRateLimitNoticeShown() {
        pages.update { it.copy(rateLimitNotice = null) }
    }

    private fun loadFirstPage(refreshing: Boolean) {
        loadJob?.cancel()
        pages.update {
            if (refreshing && it.users.isNotEmpty()) {
                // 追加読み込みの途中だった場合は取り消されるので、状態を戻しておく
                val append = if (it.append == AppendState.Loading) AppendState.Idle else it.append
                it.copy(isRefreshing = true, append = append)
            } else {
                it.copy(initialLoad = InitialLoad.Loading)
            }
        }
        loadJob = viewModelScope.launch {
            try {
                val users = userRepository.getUsers(since = null)
                pages.value = PagesState(
                    users = users,
                    initialLoad = InitialLoad.Loaded,
                    append = appendStateAfter(users),
                )
            } catch (e: GitHubException) {
                pages.update {
                    if (it.isRefreshing) {
                        // 更新に失敗したときは、表示中の一覧を残して知らせるだけにする
                        it.copy(isRefreshing = false, rateLimitNotice = e as? GitHubException.RateLimited)
                    } else {
                        it.copy(initialLoad = InitialLoad.Failed(e))
                    }
                }
            }
        }
    }

    private fun loadNextPage() {
        val since = pages.value.users.lastOrNull()?.id
        pages.update { it.copy(append = AppendState.Loading) }
        loadJob = viewModelScope.launch {
            try {
                val next = userRepository.getUsers(since = since)
                pages.update {
                    it.copy(
                        // 念のため、同じユーザーが重複しないようにする
                        users = (it.users + next).distinctBy(User::id),
                        append = appendStateAfter(next),
                    )
                }
            } catch (e: GitHubException) {
                pages.update {
                    it.copy(
                        append = AppendState.Failed(e),
                        rateLimitNotice = e as? GitHubException.RateLimited,
                    )
                }
            }
        }
    }

    private fun appendStateAfter(page: List<User>): AppendState =
        if (page.isEmpty()) AppendState.EndReached else AppendState.Idle

    private data class PagesState(
        val users: List<User> = emptyList(),
        val initialLoad: InitialLoad = InitialLoad.Loading,
        val append: AppendState = AppendState.Idle,
        val isRefreshing: Boolean = false,
        val rateLimitNotice: GitHubException.RateLimited? = null,
    )

    private companion object {
        const val KEY_QUERY = "query"
    }
}

/** ログイン名に [query] を含むユーザーに絞り込む（大文字と小文字は区別しない）。 */
internal fun List<User>.filterBy(query: String): List<User> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return this
    return filter { it.login.contains(keyword, ignoreCase = true) }
}
