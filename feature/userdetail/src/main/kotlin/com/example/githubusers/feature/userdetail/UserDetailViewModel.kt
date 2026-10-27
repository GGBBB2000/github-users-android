package com.example.githubusers.feature.userdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubusers.core.data.repository.RepoRepository
import com.example.githubusers.core.data.repository.UserRepository
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.model.RepoSort
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ユーザー詳細。プロフィール、人気のリポジトリ、リポジトリ一覧を並行して読み込む。
 *
 * 表示するユーザーごとに別のインスタンスを作る（[Factory] で [login] を渡す）。
 */
@HiltViewModel(assistedFactory = UserDetailViewModel.Factory::class)
class UserDetailViewModel @AssistedInject constructor(
    @Assisted private val login: String,
    private val userRepository: UserRepository,
    private val repoRepository: RepoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserDetailUiState(login = login))
    val uiState: StateFlow<UserDetailUiState> = _uiState.asStateFlow()

    private var reposJob: Job? = null

    init {
        loadAll()
    }

    /** プロフィールの読み込みに失敗したとき。すべて読み込み直す。 */
    fun retry() {
        loadAll()
    }

    fun onSortChange(sort: RepoSort) {
        if (sort == _uiState.value.repos.sort) return
        _uiState.update { it.copy(repos = RepoListState(sort = sort)) }
        loadRepos(page = 1)
    }

    /** 「さらに表示」 */
    fun loadMoreRepos() {
        val repos = _uiState.value.repos
        if (repos.isLoading || !repos.canLoadMore) return
        loadRepos(page = repos.page + 1)
    }

    /** リポジトリ一覧の読み込みに失敗したとき。失敗したページから読み込み直す。 */
    fun retryRepos() {
        loadRepos(page = _uiState.value.repos.page + 1)
    }

    private fun loadAll() {
        loadProfile()
        loadPopularRepos()
        _uiState.update { it.copy(repos = RepoListState(sort = it.repos.sort)) }
        loadRepos(page = 1)
    }

    private fun loadProfile() {
        _uiState.update { it.copy(profile = ProfileState.Loading) }
        viewModelScope.launch {
            val profile = try {
                ProfileState.Success(userRepository.getUserDetail(login))
            } catch (e: GitHubException) {
                ProfileState.Failed(e)
            }
            _uiState.update { it.copy(profile = profile) }
        }
    }

    private fun loadPopularRepos() {
        _uiState.update { it.copy(popularRepos = PopularReposState.Loading) }
        viewModelScope.launch {
            val popular = try {
                PopularReposState.Success(repoRepository.getPopularRepos(login))
            } catch (_: GitHubException) {
                PopularReposState.Unavailable
            }
            _uiState.update { it.copy(popularRepos = popular) }
        }
    }

    private fun loadRepos(page: Int) {
        reposJob?.cancel()
        val sort = _uiState.value.repos.sort
        _uiState.update { it.copy(repos = it.repos.copy(isLoading = true, error = null)) }
        reposJob = viewModelScope.launch {
            try {
                val next = repoRepository.getRepos(login, sort, page)
                _uiState.update { state ->
                    val current = if (page == 1) emptyList() else state.repos.repos
                    state.copy(
                        repos = state.repos.copy(
                            repos = (current + next).distinctBy { it.id },
                            isLoading = false,
                            page = page,
                            canLoadMore = next.size >= RepoRepository.PAGE_SIZE,
                        ),
                    )
                }
            } catch (e: GitHubException) {
                _uiState.update { it.copy(repos = it.repos.copy(isLoading = false, error = e)) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(login: String): UserDetailViewModel
    }
}
