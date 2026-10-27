package com.example.githubusers.feature.signin.navigation

import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import com.example.githubusers.feature.signin.SignInScreen
import kotlinx.serialization.Serializable

/** サインインの遷移先。 */
@Serializable
data object SignInNavKey : NavKey

/**
 * サインイン画面を登録する。
 *
 * @param useDialog ダイアログで表示するか（大きい画面）。false なら全画面
 * @param onClose 閉じるボタンを押したとき
 * @param onSignedIn サインインできたとき
 */
fun EntryProviderScope<NavKey>.signInEntry(
    useDialog: Boolean,
    onClose: () -> Unit,
    onSignedIn: (login: String) -> Unit,
) {
    entry<SignInNavKey>(
        metadata = if (useDialog) {
            // 幅は画面側で最大 560dp にする
            DialogSceneStrategy.dialog(DialogProperties(usePlatformDefaultWidth = false))
        } else {
            emptyMap()
        },
    ) {
        SignInScreen(isDialog = useDialog, onClose = onClose, onSignedIn = onSignedIn)
    }
}
