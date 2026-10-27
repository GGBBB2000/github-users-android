package com.example.githubusers.feature.userlist.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.example.githubusers.core.model.User
import com.example.githubusers.feature.userlist.UserListScreen
import kotlinx.serialization.Serializable

/** ユーザー一覧の遷移先。 */
@Serializable
data object UserListNavKey : NavKey

/** タブレットのリストペインの幅。 */
private val ListPaneWidth = 400.dp

/**
 * ユーザー一覧を、リスト・詳細レイアウトのリストペインとして登録する。
 *
 * @param selectedLogin 2ペイン表示で選択中のユーザー
 * @param isTwoPane 2ペイン表示か（選択中の行のハイライトとシェブロンの表示を切り替える）
 * @param detailPlaceholder 2ペイン表示でユーザーを選んでいないときの詳細ペイン
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.userListEntry(
    onUserClick: (User) -> Unit,
    selectedLogin: String?,
    isTwoPane: Boolean,
    detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit,
) {
    entry<UserListNavKey>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = detailPlaceholder) +
            ListDetailSceneStrategy.preferredPaneSize(width = ListPaneWidth),
    ) {
        UserListScreen(
            onUserClick = onUserClick,
            selectedLogin = if (isTwoPane) selectedLogin else null,
            showChevron = !isTwoPane,
        )
    }
}
