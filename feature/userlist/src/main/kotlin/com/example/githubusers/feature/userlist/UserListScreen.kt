package com.example.githubusers.feature.userlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.githubusers.core.designsystem.component.FilterField
import com.example.githubusers.core.designsystem.component.SkeletonBlock
import com.example.githubusers.core.designsystem.icon.GuIcons
import com.example.githubusers.core.designsystem.theme.GitHubUsersTheme
import com.example.githubusers.core.model.AccountType
import com.example.githubusers.core.model.AuthState
import com.example.githubusers.core.model.GitHubException
import com.example.githubusers.core.model.User
import com.example.githubusers.core.ui.GitHubErrorState
import com.example.githubusers.core.ui.ShowSnackbar
import com.example.githubusers.core.ui.UserAvatar
import com.example.githubusers.core.ui.label
import com.example.githubusers.core.ui.rateLimitMessage
import com.example.githubusers.core.ui.rememberExternalActions
import kotlinx.coroutines.launch

/** 末尾からこの件数以内まで表示されたら、続きを読み込む。 */
private const val LOAD_MORE_THRESHOLD = 10

/**
 * ユーザー一覧（リストペイン）。
 *
 * @param selectedLogin 2ペイン表示で選択中のユーザー。1ペインのときは null
 * @param showChevron 行の右端にシェブロンを出すか（スマホのみ）
 */
@Composable
fun UserListScreen(
    onUserClick: (User) -> Unit,
    onSignInClick: () -> Unit,
    onShowSnackbar: ShowSnackbar,
    modifier: Modifier = Modifier,
    selectedLogin: String? = null,
    showChevron: Boolean = true,
    viewModel: UserListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val externalActions = rememberExternalActions()
    UserListScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onUserClick = onUserClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onRateLimitNoticeShown = viewModel::onRateLimitNoticeShown,
        onSignInClick = onSignInClick,
        onSignOut = viewModel::signOut,
        onShowSnackbar = onShowSnackbar,
        onOpenUrl = externalActions::openUrl,
        selectedLogin = selectedLogin,
        showChevron = showChevron,
        modifier = modifier,
    )
}

/** GitHub 側でアプリの許可を取り消すページ（Authorized GitHub Apps）。 */
private const val GITHUB_APP_AUTHORIZATIONS_URL = "https://github.com/settings/apps/authorizations"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserListScreen(
    uiState: UserListUiState,
    onQueryChange: (String) -> Unit,
    onUserClick: (User) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRateLimitNoticeShown: () -> Unit,
    onSignInClick: () -> Unit,
    onSignOut: () -> Unit,
    onShowSnackbar: ShowSnackbar,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedLogin: String? = null,
    showChevron: Boolean = true,
) {
    uiState.rateLimitNotice?.let { notice ->
        // 未ログインなら、再試行ではなくサインインを勧める（上限が解除されるまでは再試行しても失敗するため）
        val canSignIn = uiState.canSignIn
        val message = if (canSignIn) {
            stringResource(R.string.feature_userlist_rate_limit_sign_in)
        } else {
            rateLimitMessage(notice.resetAt)
        }
        val actionLabel = stringResource(
            if (canSignIn) R.string.feature_userlist_sign_in_action else R.string.feature_userlist_retry,
        )
        LaunchedEffect(notice) {
            val actionPerformed = onShowSnackbar(message, actionLabel)
            onRateLimitNoticeShown()
            if (actionPerformed) if (canSignIn) onSignInClick() else onRetry()
        }
    }

    val scope = rememberCoroutineScope()
    val signedOutMessage = stringResource(R.string.feature_userlist_signed_out)
    val settingsLabel = stringResource(R.string.feature_userlist_github_settings)
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    if (showSignOutDialog) {
        SignOutDialog(
            onConfirm = {
                showSignOutDialog = false
                onSignOut()
                scope.launch {
                    // アプリから GitHub 側の許可は取り消せないので、取り消せるページを案内する
                    if (onShowSnackbar(signedOutMessage, settingsLabel)) onOpenUrl(GITHUB_APP_AUTHORIZATIONS_URL)
                }
            },
            onDismiss = { showSignOutDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_userlist_title)) },
                actions = {
                    OverflowMenu(
                        authState = uiState.authState,
                        isSignInAvailable = uiState.isSignInAvailable,
                        onReload = onRefresh,
                        onSignIn = onSignInClick,
                        onSignOut = { showSignOutDialog = true },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            FilterField(
                query = uiState.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.feature_userlist_filter_placeholder),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            when (val initial = uiState.initialLoad) {
                InitialLoad.Loading -> SkeletonList()
                is InitialLoad.Failed -> GitHubErrorState(
                    error = initial.error,
                    onRetry = onRetry,
                    onSignIn = if (uiState.canSignIn) onSignInClick else null,
                    modifier = Modifier.fillMaxSize(),
                )
                InitialLoad.Loaded -> PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    UserList(
                        uiState = uiState,
                        onUserClick = onUserClick,
                        onLoadMore = onLoadMore,
                        onRetry = onRetry,
                        selectedLogin = selectedLogin,
                        showChevron = showChevron,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserList(
    uiState: UserListUiState,
    onUserClick: (User) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    selectedLogin: String?,
    showChevron: Boolean,
    listState: LazyListState = rememberLazyListState(),
) {
    // 絞り込み中は自動で読み込まない（一致するユーザーが少ないと、上限まで読み込み続けてしまうため）
    val autoLoad = !uiState.isFiltering
    val shouldLoadMore by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState, autoLoad) {
        if (!autoLoad) return@LaunchedEffect
        snapshotFlow { shouldLoadMore }.collect { if (it) onLoadMore() }
    }

    // 行を選んだら絞り込み欄からフォーカスを外し、キーボードを閉じる
    val focusManager = LocalFocusManager.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(uiState.users, key = { _, user -> user.id }) { index, user ->
            UserRow(
                user = user,
                tone = index,
                selected = user.login == selectedLogin,
                showChevron = showChevron,
                onClick = {
                    focusManager.clearFocus()
                    onUserClick(user)
                },
            )
        }
        item(key = "footer") {
            ListFooter(uiState = uiState, onLoadMore = onLoadMore, onRetry = onRetry)
        }
    }
}

@Composable
private fun UserRow(
    user: User,
    tone: Int,
    selected: Boolean,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(user.login, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                text = stringResource(R.string.feature_userlist_supporting, user.id, user.type.label),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        },
        leadingContent = { UserAvatar(avatarUrl = user.avatarUrl, login = user.login, tone = tone) },
        trailingContent = if (showChevron) {
            { Icon(GuIcons.Chevron, contentDescription = null, tint = colors.onSurfaceVariant) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) colors.secondaryContainer else Color.Transparent,
        ),
        modifier = Modifier
            // 2ペインでは選択中の行を角丸のハイライトで示すので、左右に少し余白を取る
            .padding(horizontal = if (showChevron) 0.dp else 8.dp)
            .clip(RoundedCornerShape(if (showChevron) 0.dp else 28.dp))
            .heightIn(min = 72.dp)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ListFooter(
    uiState: UserListUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        if (uiState.isFiltering) {
            if (uiState.users.isEmpty()) {
                FooterText(stringResource(R.string.feature_userlist_no_match, uiState.query.trim()))
            }
            FooterText(stringResource(R.string.feature_userlist_filtering, uiState.loadedCount))
        }
        when (val append = uiState.append) {
            AppendState.Loading -> Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.feature_userlist_loading_more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            is AppendState.Failed -> {
                HorizontalDivider(color = colors.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.feature_userlist_load_more_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.feature_userlist_retry))
                    }
                }
            }
            AppendState.Idle -> if (uiState.isFiltering) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onLoadMore) {
                        Text(stringResource(R.string.feature_userlist_load_more))
                    }
                }
            }
            AppendState.EndReached -> FooterText(
                stringResource(
                    if (uiState.loadedCount == 0) R.string.feature_userlist_empty else R.string.feature_userlist_end,
                ),
            )
        }
    }
}

@Composable
private fun FooterText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SkeletonList() {
    Column(Modifier.fillMaxSize()) {
        val lineWidths = listOf(120, 96, 140, 88, 128, 104, 112, 92, 136)
        lineWidths.forEach { width ->
            Row(
                modifier = Modifier.height(72.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(Modifier.size(48.dp), shape = RoundedCornerShape(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.width(width.dp).height(14.dp))
                    SkeletonBlock(Modifier.width(96.dp).height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    authState: AuthState,
    isSignInAvailable: Boolean,
    onReload: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        IconButton(
            onClick = { expanded = true },
            // メニューを開いている間は、どのボタンから開いたかがわかるように背景を付ける
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (expanded) colors.surfaceContainerHighest else Color.Transparent,
            ),
        ) {
            Icon(GuIcons.More, contentDescription = stringResource(R.string.feature_userlist_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val reload = @Composable {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_userlist_reload)) },
                    leadingIcon = { Icon(GuIcons.Refresh, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onReload()
                    },
                )
            }
            when (authState) {
                is AuthState.SignedIn -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_userlist_signed_in_as, authState.login)) },
                        leadingIcon = {
                            UserAvatar(avatarUrl = authState.avatarUrl, login = authState.login, size = 24.dp)
                        },
                        onClick = {},
                        enabled = false,
                        colors = MenuDefaults.itemColors(disabledTextColor = colors.onSurfaceVariant),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_userlist_sign_out)) },
                        leadingIcon = { Icon(GuIcons.SignOut, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onSignOut()
                        },
                    )
                    HorizontalDivider()
                    reload()
                }
                AuthState.SignedOut -> {
                    reload()
                    if (isSignInAvailable) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feature_userlist_sign_in)) },
                            leadingIcon = { Icon(GuIcons.SignIn, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onSignIn()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_userlist_sign_out_title)) },
        text = { Text(stringResource(R.string.feature_userlist_sign_out_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.feature_userlist_sign_out)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_userlist_cancel)) }
        },
    )
}

private val previewUsers = listOf(
    "mojombo", "defunkt", "pjhyett", "wycats", "ezmobius", "ivey", "evanphx", "vanpelt",
).mapIndexed { i, login -> User(i + 1L, login, "", AccountType.User) }

@Preview
@Composable
private fun UserListScreenPreview() {
    GitHubUsersTheme {
        UserListScreen(
            uiState = UserListUiState(
                users = previewUsers,
                loadedCount = previewUsers.size,
                initialLoad = InitialLoad.Loaded,
                append = AppendState.Loading,
            ),
            onQueryChange = {}, onUserClick = {}, onLoadMore = {}, onRetry = {}, onRefresh = {},
            onRateLimitNoticeShown = {}, onSignInClick = {}, onSignOut = {}, onShowSnackbar = { _, _ -> false },
            onOpenUrl = {},
        )
    }
}

@Preview
@Composable
private fun UserListScreenErrorPreview() {
    GitHubUsersTheme {
        UserListScreen(
            uiState = UserListUiState(
                initialLoad = InitialLoad.Failed(GitHubException.Network(java.io.IOException())),
            ),
            onQueryChange = {}, onUserClick = {}, onLoadMore = {}, onRetry = {}, onRefresh = {},
            onRateLimitNoticeShown = {}, onSignInClick = {}, onSignOut = {}, onShowSnackbar = { _, _ -> false },
            onOpenUrl = {},
        )
    }
}
