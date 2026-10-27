package com.example.githubusers.feature.userdetail.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.example.githubusers.feature.userdetail.UserDetailScreen
import kotlinx.serialization.Serializable

/** ユーザー詳細の遷移先。 */
@Serializable
data class UserDetailNavKey(val login: String) : NavKey

/**
 * ユーザー詳細を、リスト・詳細レイアウトの詳細ペインとして登録する。
 *
 * @param isTwoPane 2ペイン表示か
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.userDetailEntry(
    isTwoPane: Boolean,
    onBack: () -> Unit,
) {
    entry<UserDetailNavKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
        UserDetailScreen(login = key.login, isTwoPane = isTwoPane, onBack = onBack)
    }
}
