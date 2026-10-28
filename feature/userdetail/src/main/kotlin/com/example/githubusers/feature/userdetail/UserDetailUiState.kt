package com.example.githubusers.feature.userdetail

import com.example.githubusers.core.model.ActivitySummary
import com.example.githubusers.core.model.ContributionCalendar
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.RepoSort
import com.example.githubusers.core.model.UserDetail

data class UserDetailUiState(
    val login: String,
    val profile: ProfileState = ProfileState.Loading,
    val popularRepos: PopularReposState = PopularReposState.Loading,
    val repos: RepoListState = RepoListState(),
    val contributions: ContributionState = ContributionState.Loading,
    val summary: SummaryState = SummaryState.Hidden,
    /** サインインの入口を出せるか（未ログインで、サインインの設定がある） */
    val canSignIn: Boolean = false,
)

/** 草（コントリビューションのカレンダー）。GraphQL API を使うので、サインインが必要。 */
sealed interface ContributionState {
    /** セクションごと出さない（Organization や Bot、サインインの設定がない） */
    data object Hidden : ContributionState
    data object Loading : ContributionState

    /** サインインを勧めるカードを出す */
    data object SignedOut : ContributionState
    data class Failed(val error: GitHubException) : ContributionState
    data class Success(val calendar: ContributionCalendar) : ContributionState
}

/** 端末上の AI による活動の要約。 */
sealed interface SummaryState {
    /** 何も出さない（端末が対応していない、確かめている途中、プロフィールがまだない） */
    data object Hidden : SummaryState

    /** 要約できるほどの活動がない */
    data object NoData : SummaryState

    /** 「要約を作る」 */
    data object Entry : SummaryState

    /** モデルのダウンロードが必要 */
    data object NeedsDownload : SummaryState

    /** @param totalBytes 全体の量。わからなければ null（進み具合は不確定の表示にする） */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : SummaryState
    data object DownloadFailed : SummaryState
    data object Generating : SummaryState

    /** @param canIncludeContributions 草なしで作った後にサインインして、草を含めて作り直せる */
    data class Done(val summary: ActivitySummary, val canIncludeContributions: Boolean) : SummaryState
    data class Failed(val reason: SummaryFailure) : SummaryState
}

enum class SummaryFailure {
    /** 混み合っている。しばらくすれば作れる */
    Busy,

    /** 今日の上限に達した。再試行は出さない */
    QuotaExceeded,
    Other,
}

sealed interface ProfileState {
    data object Loading : ProfileState
    data class Failed(val error: GitHubException) : ProfileState
    data class Success(val user: UserDetail) : ProfileState
}

sealed interface PopularReposState {
    data object Loading : PopularReposState

    /** 取得できなかったときはセクションごと出さない（Search API の制限が別枠で厳しいため） */
    data object Unavailable : PopularReposState
    data class Success(val repos: List<Repo>) : PopularReposState
}

data class RepoListState(
    val sort: RepoSort = RepoSort.Updated,
    val repos: List<Repo> = emptyList(),
    val isLoading: Boolean = true,
    val error: GitHubException? = null,
    /** 読み込み済みのページ数 */
    val page: Int = 0,
    val canLoadMore: Boolean = false,
)
