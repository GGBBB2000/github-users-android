package com.example.githubusers.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.githubusers.feature.userdetail.UserDetailPlaceholder
import com.example.githubusers.feature.userdetail.navigation.UserDetailNavKey
import com.example.githubusers.feature.userdetail.navigation.userDetailEntry
import com.example.githubusers.feature.userlist.navigation.UserListNavKey
import com.example.githubusers.feature.userlist.navigation.userListEntry

/**
 * 一覧と詳細を組み立てる。
 *
 * Navigation 3 の [NavDisplay] と、リスト・詳細レイアウトの SceneStrategy を使う。
 * スマホでは一覧 → 詳細の1ペイン遷移、タブレットでは左に一覧、右に詳細の2ペインになる。
 * 一覧と詳細の機能モジュールは互いを知らないので、選択されたユーザーの受け渡しはここで行う。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GitHubUsersApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(UserListNavKey)
    // SceneStrategy と同じ判定で、2ペイン表示かどうかを決める
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val isTwoPane = directive.maxHorizontalPartitions > 1
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
        // 2ペインでも、戻る操作で詳細を閉じて未選択の状態に戻す（初期値ではアプリが閉じてしまう）
        backNavigationBehavior = BackNavigationBehavior.PopUntilContentChange,
        directive = directive,
    )
    val selectedLogin = (backStack.lastOrNull() as? UserDetailNavKey)?.login

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                // 画面ごとに ViewModel を持ち、画面を閉じたら破棄する
                rememberViewModelStoreNavEntryDecorator(),
            ),
            sceneStrategies = listOf(listDetailStrategy),
            entryProvider = entryProvider {
                userListEntry(
                    onUserClick = { user ->
                        if (user.login != selectedLogin) {
                            // 詳細は常に1つだけにする（戻る操作で前に選んだユーザーをたどらないように）
                            backStack.removeAll { it is UserDetailNavKey }
                            backStack.add(UserDetailNavKey(user.login))
                        }
                    },
                    selectedLogin = selectedLogin,
                    isTwoPane = isTwoPane,
                    detailPlaceholder = { UserDetailPlaceholder() },
                )
                userDetailEntry(
                    isTwoPane = isTwoPane,
                    onBack = { backStack.removeLastOrNull() },
                )
            },
        )
    }
}
