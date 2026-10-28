package com.example.githubusers.feature.userdetail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.githubusers.core.designsystem.component.SkeletonBlock
import com.example.githubusers.core.designsystem.icon.GuIcons
import com.example.githubusers.core.designsystem.theme.GuShapes
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.UserDetail
import com.example.githubusers.core.ui.ExternalActions
import com.example.githubusers.core.ui.GitHubErrorState
import com.example.githubusers.core.ui.UserAvatar
import com.example.githubusers.core.ui.formatCount
import com.example.githubusers.core.ui.label
import com.example.githubusers.core.ui.relativeTimeText

/** タブレット（2ペイン）の詳細ペイン。プロフィールとアクティビティは常に表示する。 */
@Composable
internal fun ExpandedUserDetail(
    uiState: UserDetailUiState,
    callbacks: UserDetailCallbacks,
    externalActions: ExternalActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = GuShapes.Card,
        modifier = modifier.expandedPaneFrame(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (val profile = uiState.profile) {
                ProfileState.Loading -> item { ExpandedSkeleton() }
                is ProfileState.Failed -> item {
                    GitHubErrorState(
                        error = profile.error,
                        onRetry = callbacks.onRetry,
                        onSignIn = if (uiState.canSignIn) callbacks.onSignIn else null,
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
                is ProfileState.Success -> expandedContent(profile.user, uiState, callbacks, externalActions)
            }
        }
    }
}

private fun LazyListScope.expandedContent(
    user: UserDetail,
    uiState: UserDetailUiState,
    callbacks: UserDetailCallbacks,
    externalActions: ExternalActions,
) {
    item(key = "header") { Header(user, callbacks) }
    user.bio?.let { bio ->
        item(key = "bio") { Text(bio, style = MaterialTheme.typography.bodyLarge) }
    }
    item(key = "stats") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(user.followers, stringResource(R.string.feature_userdetail_followers), Modifier.weight(1f))
            StatCard(user.following, stringResource(R.string.feature_userdetail_following), Modifier.weight(1f))
            StatCard(user.publicRepos, stringResource(R.string.feature_userdetail_public_repos), Modifier.weight(1f))
            StatCard(user.publicGists, stringResource(R.string.feature_userdetail_public_gists), Modifier.weight(1f))
        }
    }
    if (uiState.summary != SummaryState.Hidden) {
        item(key = "summary") {
            SummarySection(state = uiState.summary, isTablet = true, callbacks = callbacks.summary)
        }
    }
    item(key = "profileAndActivity") {
        val surface = MaterialTheme.colorScheme.surface
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.feature_userdetail_section_profile))
                InfoList(containerColor = surface) {
                    if (listOf(user.company, user.location, user.blog, user.twitterUsername).all { it == null }) {
                        Text(
                            stringResource(R.string.feature_userdetail_no_profile),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        ProfileRows(user, externalActions, includeEmail = false)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.feature_userdetail_section_activity))
                InfoList(containerColor = surface) {
                    JoinedRow(user)
                    InfoDivider()
                    UpdatedRow(user)
                    InfoDivider()
                    EmailRow(user, externalActions)
                }
            }
        }
    }
    if (uiState.contributions != ContributionState.Hidden) {
        item(key = "contributions") {
            ContributionSection(
                state = uiState.contributions,
                isTablet = true,
                onSignIn = callbacks.onSignIn,
                onRetry = callbacks.onRetryContributions,
            )
        }
    }
    val popular = uiState.popularRepos
    if (popular is PopularReposState.Success && popular.repos.isNotEmpty()) {
        item(key = "popular") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.feature_userdetail_section_popular))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(POPULAR_COLUMNS) { index ->
                        val repo = popular.repos.getOrNull(index)
                        if (repo != null) {
                            PopularRepoCard(
                                repo = repo,
                                containerColor = MaterialTheme.colorScheme.surface,
                                onClick = { callbacks.openRepo(repo) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
    item(key = "repos") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.feature_userdetail_section_repos))
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.feature_userdetail_repo_count, user.publicRepos),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SortChips(selected = uiState.repos.sort, onSelect = callbacks.onSortChange)
            }
            if (uiState.repos.repos.isNotEmpty()) {
                InfoList(containerColor = MaterialTheme.colorScheme.surface) {
                    uiState.repos.repos.forEachIndexed { index, repo ->
                        if (index > 0) InfoDivider()
                        ExpandedRepoRow(repo, onClick = { callbacks.openRepo(repo) })
                    }
                }
            }
            RepoListFooter(
                state = uiState.repos,
                onLoadMore = callbacks.onLoadMoreRepos,
                onRetry = callbacks.onRetryRepos,
            )
        }
    }
}

private const val POPULAR_COLUMNS = 3

@Composable
private fun Header(user: UserDetail, callbacks: UserDetailCallbacks) {
    val colors = MaterialTheme.colorScheme
    val shareTitle = stringResource(R.string.feature_userdetail_share_title, user.login)
    Row(verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(avatarUrl = user.avatarUrl, login = user.login, size = 104.dp)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(user.name ?: user.login, style = MaterialTheme.typography.headlineMedium)
            Text("@${user.login}", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(user.type.label)
                Label("ID ${user.id}", outlined = true)
            }
        }
        Spacer(Modifier.width(16.dp))
        IconButton(onClick = { callbacks.onShare(user.htmlUrl, shareTitle) }) {
            Icon(GuIcons.Share, contentDescription = stringResource(R.string.feature_userdetail_share), tint = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { callbacks.onOpenUrl(user.htmlUrl) },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(GuIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.feature_userdetail_open_in_github))
        }
    }
}

@Composable
private fun StatCard(value: Int, label: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = GuShapes.InnerList, modifier = modifier) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatCount(value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        }
    }
}

/** タブレットのリポジトリ行。名前と説明を左、言語・★・更新日を右に並べる。 */
@Composable
private fun ExpandedRepoRow(repo: Repo, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(repo.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            repo.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides colors.onSurfaceVariant,
            androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelMedium,
        ) {
            Box(Modifier.width(96.dp)) { repo.language?.let { LanguageTag(it) } }
            Box(Modifier.width(56.dp)) { StarCount(repo.stargazersCount) }
            Text(
                stringResource(R.string.feature_userdetail_repo_updated, relativeTimeText(repo.updatedAt)),
                modifier = Modifier.width(120.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ExpandedSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(Modifier.size(104.dp), shape = RoundedCornerShape(52.dp))
            Spacer(Modifier.width(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.width(240.dp).height(28.dp))
                SkeletonBlock(Modifier.width(120.dp).height(16.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { SkeletonBlock(Modifier.weight(1f).height(88.dp), shape = GuShapes.InnerList) }
        }
        SkeletonBlock(Modifier.fillMaxWidth().height(180.dp), shape = GuShapes.InnerList)
    }
}
