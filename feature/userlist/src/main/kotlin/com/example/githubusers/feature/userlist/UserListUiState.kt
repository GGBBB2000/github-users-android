package com.example.githubusers.feature.userlist

import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.model.User

data class UserListUiState(
    /** 絞り込みの入力 */
    val query: String = "",
    /** 絞り込み後のユーザー */
    val users: List<User> = emptyList(),
    /** 読み込み済みのユーザーの数（絞り込み前） */
    val loadedCount: Int = 0,
    val initialLoad: InitialLoad = InitialLoad.Loading,
    val append: AppendState = AppendState.Idle,
    val isRefreshing: Boolean = false,
    /** null でなければ、レート制限の Snackbar を出す */
    val rateLimitNotice: GitHubException.RateLimited? = null,
) {
    val isFiltering: Boolean get() = query.isNotBlank()
}

sealed interface InitialLoad {
    data object Loading : InitialLoad
    data object Loaded : InitialLoad
    data class Failed(val error: GitHubException) : InitialLoad
}

sealed interface AppendState {
    /** 続きを読み込める */
    data object Idle : AppendState
    data object Loading : AppendState
    data class Failed(val error: GitHubException) : AppendState

    /** 最後まで読み込んだ */
    data object EndReached : AppendState
}
