package com.example.githubusers.feature.userdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubusers.core.data.repository.ActivitySummaryRepository
import com.example.githubusers.core.data.repository.AuthRepository
import com.example.githubusers.core.data.repository.ContributionRepository
import com.example.githubusers.core.data.repository.RepoRepository
import com.example.githubusers.core.data.repository.UserRepository
import com.example.githubusers.core.data.summary.ActivitySummaryInput
import com.example.githubusers.core.data.summary.hasEnoughActivityForSummary
import com.example.githubusers.core.model.AccountType
import com.example.githubusers.core.model.ActivitySummary
import com.example.githubusers.core.model.AuthState
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.model.RepoSort
import com.example.githubusers.core.model.SummaryException
import com.example.githubusers.core.model.SummaryModelState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ユーザー詳細。プロフィール、人気のリポジトリ、リポジトリ一覧、草を並行して読み込む。
 *
 * 草は GraphQL API を使うので、サインインしているときだけ読み込む。サインインしていなければ、サインインを勧める。
 * 表示するユーザーごとに別のインスタンスを作る（[Factory] で [login] を渡す）。
 * レート制限で失敗した後にサインインしたら、失敗した読み込みをやり直す。
 *
 * 活動の要約は、押されたときだけ作る（端末上の AI には回数と電池の上限があるため）。
 * 作った要約は [ActivitySummaryRepository] がアプリ全体で覚えているので、開き直しても出る。
 */
@HiltViewModel(assistedFactory = UserDetailViewModel.Factory::class)
class UserDetailViewModel @AssistedInject constructor(
    @Assisted private val login: String,
    private val userRepository: UserRepository,
    private val repoRepository: RepoRepository,
    private val contributionRepository: ContributionRepository,
    private val authRepository: AuthRepository,
    private val summaryRepository: ActivitySummaryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserDetailUiState(login = login))
    val uiState: StateFlow<UserDetailUiState> = _uiState.asStateFlow()

    private var reposJob: Job? = null
    private var contributionsJob: Job? = null
    private var summaryJob: Job? = null
    private val summaryGeneration = MutableStateFlow<SummaryGeneration>(SummaryGeneration.Idle)

    private val _summaryMessages = Channel<SummaryFailure>(Channel.BUFFERED)

    /** 要約がある状態で「もう一度作る」が失敗したとき（要約は残して Snackbar で伝える）。 */
    val summaryMessages: Flow<SummaryFailure> = _summaryMessages.receiveAsFlow()

    /** Organization や Bot のように、草がないアカウントか（わかった時点で true にする） */
    private var hasNoContributions = false

    init {
        loadAll()
        viewModelScope.launch {
            var previous: AuthState? = null
            authRepository.authState.collect { state ->
                _uiState.update {
                    it.copy(canSignIn = authRepository.isSignInAvailable && state is AuthState.SignedOut)
                }
                onAuthStateChanged(state)
                if (previous is AuthState.SignedOut && state is AuthState.SignedIn) onSignedIn()
                previous = state
            }
        }
        summaryRepository.refreshModelState()
        viewModelScope.launch {
            combine(
                summaryRepository.modelState,
                summaryRepository.summary(login),
                summaryGeneration,
                _uiState.map { it.profile to it.contributions }.distinctUntilChanged(),
            ) { model, summary, generation, (profile, contributions) ->
                summaryState(model, summary, generation, profile, contributions)
            }.collect { summary -> _uiState.update { it.copy(summary = summary) } }
        }
    }

    /** 「要約を作る」「もう一度作る」「作り直す」「再試行」 */
    fun generateSummary() {
        if (summaryJob?.isActive == true) return
        val hadSummary = _uiState.value.summary is SummaryState.Done
        summaryGeneration.value = SummaryGeneration.Running
        summaryJob = viewModelScope.launch {
            // 草とリポジトリの読み込みが終わってから作る（途中の値で作らないように）
            val state = _uiState.first { it.isReadyForSummary() }
            val user = (state.profile as? ProfileState.Success)?.user
            if (user == null) {
                summaryGeneration.value = SummaryGeneration.Idle
                return@launch
            }
            summaryGeneration.value = try {
                summaryRepository.summarize(
                    ActivitySummaryInput(
                        user = user,
                        calendar = (state.contributions as? ContributionState.Success)?.calendar,
                        repos = state.repos.repos,
                        popularRepos = (state.popularRepos as? PopularReposState.Success)?.repos.orEmpty(),
                    ),
                )
                SummaryGeneration.Idle
            } catch (_: SummaryException.Interrupted) {
                // アプリが前面でなくなった。中止と同じに扱う
                SummaryGeneration.Idle
            } catch (e: SummaryException) {
                val reason = e.toFailure()
                if (hadSummary) {
                    _summaryMessages.trySend(reason)
                    SummaryGeneration.Idle
                } else {
                    SummaryGeneration.Failed(reason)
                }
            }
        }
    }

    /** 「中止」 */
    fun cancelSummary() {
        summaryJob?.cancel()
        summaryGeneration.value = SummaryGeneration.Idle
    }

    /** 「ダウンロード」と、ダウンロードの失敗の「再試行」 */
    fun downloadSummaryModel() {
        summaryRepository.downloadModel()
    }

    /** レート制限で失敗していた読み込みを、サインインした後にやり直す。 */
    private fun onSignedIn() {
        val state = _uiState.value
        if ((state.profile as? ProfileState.Failed)?.error is GitHubException.RateLimited) {
            loadAll()
            return
        }
        if (state.popularRepos == PopularReposState.Unavailable) loadPopularRepos()
        if (state.repos.error is GitHubException.RateLimited) retryRepos()
    }

    /** 草の読み込みに失敗したとき。 */
    fun retryContributions() {
        loadContributions()
    }

    private fun onAuthStateChanged(state: AuthState) {
        if (!authRepository.isSignInAvailable || hasNoContributions) {
            _uiState.update { it.copy(contributions = ContributionState.Hidden) }
            return
        }
        when (state) {
            AuthState.SignedOut -> {
                // 読み込み中に 401 で自動的にサインアウトしたときも、ここでサインインを勧める表示に変わる
                contributionsJob?.cancel()
                _uiState.update { it.copy(contributions = ContributionState.SignedOut) }
            }
            is AuthState.SignedIn -> {
                if (_uiState.value.contributions !is ContributionState.Success) loadContributions()
            }
        }
    }

    private fun loadContributions() {
        contributionsJob?.cancel()
        _uiState.update { it.copy(contributions = ContributionState.Loading) }
        contributionsJob = viewModelScope.launch {
            val contributions = try {
                ContributionState.Success(contributionRepository.getContributionCalendar(login))
            } catch (_: GitHubException.NotFound) {
                // Organization と Bot には草がない
                hasNoContributions = true
                ContributionState.Hidden
            } catch (e: GitHubException) {
                ContributionState.Failed(e)
            }
            _uiState.update { it.copy(contributions = contributions) }
        }
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
        if (_uiState.value.contributions is ContributionState.Failed) loadContributions()
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
            if (profile is ProfileState.Success && profile.user.type != AccountType.User) {
                // Organization と Bot には草がないので、問い合わせずに隠す
                hasNoContributions = true
                contributionsJob?.cancel()
                _uiState.update { it.copy(contributions = ContributionState.Hidden) }
            }
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

/** この画面で要約を作っているか。 */
internal sealed interface SummaryGeneration {
    data object Idle : SummaryGeneration
    data object Running : SummaryGeneration
    data class Failed(val reason: SummaryFailure) : SummaryGeneration
}

private fun UserDetailUiState.isReadyForSummary(): Boolean =
    profile !is ProfileState.Loading &&
        contributions != ContributionState.Loading &&
        popularRepos != PopularReposState.Loading &&
        !(repos.isLoading && repos.repos.isEmpty())

private fun SummaryException.toFailure(): SummaryFailure = when (this) {
    is SummaryException.Busy -> SummaryFailure.Busy
    is SummaryException.QuotaExceeded -> SummaryFailure.QuotaExceeded
    else -> SummaryFailure.Other
}

/**
 * 要約のセクションに出すもの。
 *
 * 端末が対応していなければ、ほかの条件に関係なく何も出さない。作った要約があれば、モデルの状態に関係なく出す。
 */
internal fun summaryState(
    model: SummaryModelState,
    summary: ActivitySummary?,
    generation: SummaryGeneration,
    profile: ProfileState,
    contributions: ContributionState,
): SummaryState {
    if (model == SummaryModelState.Checking || model == SummaryModelState.Unavailable) return SummaryState.Hidden
    val user = (profile as? ProfileState.Success)?.user ?: return SummaryState.Hidden
    if (generation == SummaryGeneration.Running) return SummaryState.Generating
    if (summary != null) {
        return SummaryState.Done(
            summary = summary,
            canIncludeContributions = !summary.includesContributions && contributions is ContributionState.Success,
        )
    }
    if (generation is SummaryGeneration.Failed) return SummaryState.Failed(generation.reason)
    val totalContributions = when (contributions) {
        is ContributionState.Success -> contributions.calendar.totalContributions
        // リポジトリがないときは、草の件数がわかるまで判断しない
        ContributionState.Loading -> if (user.publicRepos == 0) return SummaryState.Hidden else null
        else -> null
    }
    if (!hasEnoughActivityForSummary(user.publicRepos, totalContributions)) return SummaryState.NoData
    return when (model) {
        SummaryModelState.Available -> SummaryState.Entry
        SummaryModelState.Downloadable -> SummaryState.NeedsDownload
        is SummaryModelState.Downloading -> SummaryState.Downloading(model.downloadedBytes, model.totalBytes)
        SummaryModelState.DownloadFailed -> SummaryState.DownloadFailed
        SummaryModelState.Checking, SummaryModelState.Unavailable -> SummaryState.Hidden
    }
}
