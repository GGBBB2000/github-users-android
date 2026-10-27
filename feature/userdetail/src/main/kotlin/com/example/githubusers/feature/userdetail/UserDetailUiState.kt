package com.example.githubusers.feature.userdetail

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
