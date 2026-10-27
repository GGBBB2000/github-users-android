package com.example.githubusers.feature.userdetail

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.RepoSort
import com.example.githubusers.core.ui.rememberExternalActions

/**
 * ユーザー詳細（詳細ペイン）。
 *
 * 2ペイン表示で詳細ペインが十分に広いときはタブレット用のレイアウトにする。
 * 横向きのスマホのように2ペインでも詳細ペインが狭いときは、スマホ用のレイアウト（戻るボタンなし）にする。
 *
 * @param isTwoPane 2ペイン表示か
 * @param onBack 1ペイン表示で戻るボタンを押したとき
 * @param onSignInClick レート制限の表示でサインインを押したとき
 */
@Composable
fun UserDetailScreen(
    login: String,
    isTwoPane: Boolean,
    onBack: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 画面（NavEntry）ごとに ViewModel を持つので、画面を閉じると ViewModel も破棄される
    val viewModel = hiltViewModel<UserDetailViewModel, UserDetailViewModel.Factory> { factory ->
        factory.create(login)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val externalActions = rememberExternalActions()
    val callbacks = UserDetailCallbacks(
        onBack = onBack,
        onRetry = viewModel::retry,
        onSignIn = onSignInClick,
        onSortChange = viewModel::onSortChange,
        onLoadMoreRepos = viewModel::loadMoreRepos,
        onRetryRepos = viewModel::retryRepos,
        onRetryContributions = viewModel::retryContributions,
        onOpenUrl = externalActions::openUrl,
        onShare = externalActions::shareText,
    )
    BoxWithConstraints(modifier) {
        if (isTwoPane && maxWidth >= ExpandedLayoutMinWidth) {
            ExpandedUserDetail(uiState, callbacks, externalActions)
        } else {
            CompactUserDetail(uiState, callbacks, externalActions, showBack = !isTwoPane)
        }
    }
}

/** タブレット用のレイアウトにする詳細ペインの最小幅（4列の統計と2列のプロフィールが収まる幅）。 */
private val ExpandedLayoutMinWidth = 640.dp

internal data class UserDetailCallbacks(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onSignIn: () -> Unit,
    val onSortChange: (RepoSort) -> Unit,
    val onLoadMoreRepos: () -> Unit,
    val onRetryRepos: () -> Unit,
    val onRetryContributions: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onShare: (text: String, title: String) -> Unit,
) {
    fun openRepo(repo: Repo) = onOpenUrl(repo.htmlUrl)
}

/** プロフィールを読み込む前でも開けるように、URL はログイン名から作る。 */
internal fun profileUrl(login: String) = "https://github.com/$login"
