package com.example.githubusers.feature.userdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.githubusers.core.designsystem.component.ErrorState
import com.example.githubusers.core.designsystem.component.SkeletonBlock
import com.example.githubusers.core.designsystem.icon.GuIcons
import com.example.githubusers.core.designsystem.theme.GuShapes
import com.example.githubusers.core.model.UserDetail
import com.example.githubusers.core.ui.ExternalActions
import com.example.githubusers.core.ui.UserAvatar
import com.example.githubusers.core.ui.errorMessage
import com.example.githubusers.core.ui.formatCount
import com.example.githubusers.core.ui.label
import com.example.githubusers.core.ui.R as CoreUiR

/**
 * スマホ（1ペイン）の詳細画面。
 *
 * @param showBack 戻るボタンを出すか（2ペインで詳細ペインが狭いときにも使うので、そのときは出さない）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactUserDetail(
    uiState: UserDetailUiState,
    callbacks: UserDetailCallbacks,
    externalActions: ExternalActions,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    val login = uiState.login
    val shareTitle = stringResource(R.string.feature_userdetail_share_title, login)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = callbacks.onBack) {
                            Icon(GuIcons.Back, contentDescription = stringResource(R.string.feature_userdetail_back))
                        }
                    }
                },
                title = { Text(login) },
                actions = {
                    IconButton(onClick = { callbacks.onOpenUrl(profileUrl(login)) }) {
                        Icon(GuIcons.OpenInNew, contentDescription = stringResource(R.string.feature_userdetail_open_in_github))
                    }
                    IconButton(onClick = { callbacks.onShare(profileUrl(login), shareTitle) }) {
                        Icon(GuIcons.Share, contentDescription = stringResource(R.string.feature_userdetail_share))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            when (val profile = uiState.profile) {
                ProfileState.Loading -> item { CompactSkeleton() }
                is ProfileState.Failed -> item {
                    ErrorState(
                        title = stringResource(CoreUiR.string.core_ui_error_title),
                        message = errorMessage(profile.error),
                        onRetry = callbacks.onRetry,
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
                is ProfileState.Success -> compactContent(profile.user, uiState, callbacks, externalActions)
            }
        }
    }
}

private fun LazyListScope.compactContent(
    user: UserDetail,
    uiState: UserDetailUiState,
    callbacks: UserDetailCallbacks,
    externalActions: ExternalActions,
) {
    item(key = "profile") {
        ProfileCard(user, externalActions, Modifier.padding(horizontal = 16.dp))
    }
    item(key = "activity") {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(stringResource(R.string.feature_userdetail_section_activity), Modifier.padding(bottom = 4.dp))
            InfoList(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                JoinedRow(user)
                InfoDivider()
                UpdatedRow(user)
            }
        }
    }
    val popular = uiState.popularRepos
    if (popular is PopularReposState.Success && popular.repos.isNotEmpty()) {
        item(key = "popular") {
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    stringResource(R.string.feature_userdetail_section_popular),
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(popular.repos, key = { it.id }) { repo ->
                        PopularRepoCard(
                            repo = repo,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            onClick = { callbacks.openRepo(repo) },
                            modifier = Modifier.width(248.dp),
                        )
                    }
                }
            }
        }
    }
    item(key = "reposHeader") {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.feature_userdetail_section_repos), Modifier.weight(1f))
                Text(
                    stringResource(R.string.feature_userdetail_repo_count, user.publicRepos),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SortChips(selected = uiState.repos.sort, onSelect = callbacks.onSortChange)
        }
    }
    itemsIndexed(uiState.repos.repos, key = { _, repo -> "repo-${repo.id}" }) { index, repo ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RepoRow(repo, onClick = { callbacks.openRepo(repo) })
    }
    item(key = "reposFooter") {
        RepoListFooter(
            state = uiState.repos,
            onLoadMore = callbacks.onLoadMoreRepos,
            onRetry = callbacks.onRetryRepos,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ProfileCard(user: UserDetail, externalActions: ExternalActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // 状態は画面（NavEntry）ごとに保存されるので、別のユーザーを開くと閉じた状態から始まる
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(color = colors.surfaceContainer, shape = GuShapes.Card, modifier = modifier.fillMaxWidth()) {
        Column {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(avatarUrl = user.avatarUrl, login = user.login, size = 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(user.name ?: user.login, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "@${user.login}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Label(user.type.label)
                }
                user.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Column {
                    HorizontalDivider(color = colors.outlineVariant)
                    Spacer(Modifier.height(16.dp))
                    Row {
                        CompactStat(user.followers, stringResource(R.string.feature_userdetail_followers), Modifier.weight(1f))
                        CompactStat(user.following, stringResource(R.string.feature_userdetail_following), Modifier.weight(1f))
                        CompactStat(user.publicRepos, stringResource(R.string.feature_userdetail_repos), Modifier.weight(1f))
                        CompactStat(user.publicGists, stringResource(R.string.feature_userdetail_gists), Modifier.weight(1f))
                    }
                }
            }
            if (user.hasProfileDetails) {
                AnimatedVisibility(visible = expanded) {
                    InfoList(
                        containerColor = colors.surface,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        ProfileRows(user, externalActions, includeEmail = true)
                    }
                }
                ExpandButton(expanded = expanded, onToggle = { expanded = !expanded })
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ExpandButton(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val expandedState = stringResource(if (expanded) R.string.feature_userdetail_hide_details else R.string.feature_userdetail_show_details)
    TextButton(
        onClick = onToggle,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(48.dp)
            .semantics { stateDescription = expandedState },
    ) {
        Text(expandedState)
        Spacer(Modifier.width(8.dp))
        Icon(GuIcons.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
    }
}

@Composable
private fun CompactStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(formatCount(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactSkeleton() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = GuShapes.Card,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(72.dp), shape = RoundedCornerShape(36.dp))
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.width(160.dp).height(20.dp))
                    SkeletonBlock(Modifier.width(96.dp).height(14.dp))
                }
            }
            SkeletonBlock(Modifier.fillMaxWidth().height(48.dp))
        }
    }
}
