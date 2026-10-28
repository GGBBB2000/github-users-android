package com.example.githubusers.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.example.githubusers.R
import com.example.githubusers.core.ui.ShowSnackbar
import com.example.githubusers.feature.signin.navigation.SignInNavKey
import com.example.githubusers.feature.signin.navigation.signInEntry
import com.example.githubusers.feature.userdetail.UserDetailPlaceholder
import com.example.githubusers.feature.userdetail.navigation.UserDetailNavKey
import com.example.githubusers.feature.userdetail.navigation.userDetailEntry
import com.example.githubusers.feature.userlist.navigation.UserListNavKey
import com.example.githubusers.feature.userlist.navigation.userListEntry
import kotlinx.coroutines.launch

/**
 * 一覧と詳細、サインインを組み立てる。
 *
 * Navigation 3 の [NavDisplay] と、リスト・詳細レイアウトの SceneStrategy を使う。
 * スマホでは一覧 → 詳細の1ペイン遷移、タブレットでは左に一覧、右に詳細の2ペインになる。
 * サインインは、大きい画面ではダイアログ、それ以外では全画面で表示する。
 * 機能モジュールは互いを知らないので、選択されたユーザーの受け渡しや画面の行き来はここで行う。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GitHubUsersApp(
    modifier: Modifier = Modifier,
    viewModel: GitHubUsersAppViewModel = hiltViewModel(),
) {
    val backStack = rememberNavBackStack(UserListNavKey)
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    // SceneStrategy と同じ判定で、2ペイン表示かどうかを決める
    val directive = calculatePaneScaffoldDirective(adaptiveInfo)
    val isTwoPane = directive.maxHorizontalPartitions > 1
    // サインインは、幅と高さに余裕があればダイアログにする（横向きのスマホは高さが足りないので全画面）
    val useSignInDialog = adaptiveInfo.windowSizeClass.run {
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
            isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
        // 2ペインでも、戻る操作で詳細を閉じて未選択の状態に戻す（初期値ではアプリが閉じてしまう）
        backNavigationBehavior = BackNavigationBehavior.PopUntilContentChange,
        directive = directive,
    )
    val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }
    val selectedLogin = backStack.filterIsInstance<UserDetailNavKey>().lastOrNull()?.login

    // Snackbar はアプリ全体で1つにする（サインイン画面を閉じた後や、画面を移った後も表示を続けるため）
    val snackbarHostState = remember { SnackbarHostState() }
    val onShowSnackbar: ShowSnackbar = { message, actionLabel ->
        snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
        ) == SnackbarResult.ActionPerformed
    }
    val scope = rememberCoroutineScope()
    val openSignIn = {
        if (SignInNavKey !in backStack) backStack.add(SignInNavKey)
    }

    val sessionExpiredMessage = stringResource(R.string.session_expired)
    val signInAction = stringResource(R.string.sign_in_action)
    LaunchedEffect(viewModel) {
        viewModel.sessionExpired.collect {
            if (onShowSnackbar(sessionExpiredMessage, signInAction)) openSignIn()
        }
    }
    val signedInMessage = stringResource(R.string.signed_in_as)

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize()) {
        Box {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    // 画面ごとに ViewModel を持ち、画面を閉じたら破棄する
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                // サインインのダイアログは、下にある一覧・詳細の上に重ねて表示する
                sceneStrategies = listOf(dialogStrategy, listDetailStrategy),
                entryProvider = entryProvider {
                    userListEntry(
                        onUserClick = { user ->
                            if (user.login != selectedLogin) {
                                // 詳細は常に1つだけにする（戻る操作で前に選んだユーザーをたどらないように）
                                backStack.removeAll { it is UserDetailNavKey }
                                backStack.add(UserDetailNavKey(user.login))
                            }
                        },
                        onSignInClick = openSignIn,
                        onShowSnackbar = onShowSnackbar,
                        selectedLogin = selectedLogin,
                        isTwoPane = isTwoPane,
                        detailPlaceholder = { UserDetailPlaceholder() },
                    )
                    userDetailEntry(
                        isTwoPane = isTwoPane,
                        onBack = { backStack.removeLastOrNull() },
                        onSignInClick = openSignIn,
                        onShowSnackbar = onShowSnackbar,
                    )
                    signInEntry(
                        useDialog = useSignInDialog,
                        onClose = { backStack.remove(SignInNavKey) },
                        onSignedIn = { login ->
                            backStack.remove(SignInNavKey)
                            scope.launch { onShowSnackbar(signedInMessage.format(login), null) }
                        },
                    )
                },
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
            )
        }
    }
}
