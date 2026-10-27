package com.example.githubusers.feature.userdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.githubusers.core.designsystem.icon.GuIcons
import com.example.githubusers.core.designsystem.theme.GuShapes
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.RepoSort
import com.example.githubusers.core.model.UserDetail
import com.example.githubusers.core.ui.ExternalActions
import com.example.githubusers.core.ui.dateWithRelativeText
import com.example.githubusers.core.ui.displayUrl
import com.example.githubusers.core.ui.formatCount
import com.example.githubusers.core.ui.languageColor
import com.example.githubusers.core.ui.relativeTimeText

/** セクション見出し（labelLarge、primary 色）。 */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

/** 角丸の背景に行を並べ、行の間に区切り線を入れる。 */
@Composable
internal fun InfoList(
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = containerColor, shape = GuShapes.InnerList, modifier = modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
internal fun InfoDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
}

/** アイコン＋ラベル＋値の行。 */
@Composable
internal fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
        }
    }
}

/** 所属・場所・ウェブサイト・メール・X の行を出す。値が null の行は出さない（メールは「非公開」と出す）。 */
@Composable
internal fun ProfileRows(
    user: UserDetail,
    actions: ExternalActions,
    includeEmail: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val rows = buildList<@Composable () -> Unit> {
        user.company?.let { add { InfoRow(GuIcons.Company, stringResource(R.string.feature_userdetail_company), it) } }
        user.location?.let { add { InfoRow(GuIcons.Location, stringResource(R.string.feature_userdetail_location), it) } }
        user.blog?.let { blog ->
            add {
                InfoRow(
                    icon = GuIcons.Website,
                    label = stringResource(R.string.feature_userdetail_website),
                    value = displayUrl(blog),
                    valueColor = colors.primary,
                    onClick = { actions.openUrl(blog) },
                )
            }
        }
        if (includeEmail) add { EmailRow(user, actions) }
        user.twitterUsername?.let { name ->
            add {
                InfoRow(
                    icon = GuIcons.Twitter,
                    label = stringResource(R.string.feature_userdetail_twitter),
                    value = "@$name",
                    valueColor = colors.primary,
                    onClick = { actions.openUrl("https://x.com/$name") },
                )
            }
        }
    }
    rows.forEachIndexed { index, row ->
        if (index > 0) InfoDivider()
        row()
    }
}

@Composable
internal fun EmailRow(user: UserDetail, actions: ExternalActions) {
    val email = user.email
    InfoRow(
        icon = GuIcons.Email,
        label = stringResource(R.string.feature_userdetail_email),
        value = email ?: stringResource(R.string.feature_userdetail_email_private),
        valueColor = if (email != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = email?.let { { actions.sendEmail(it) } },
    )
}

@Composable
internal fun JoinedRow(user: UserDetail) {
    InfoRow(GuIcons.Joined, stringResource(R.string.feature_userdetail_joined), dateWithRelativeText(user.createdAt))
}

@Composable
internal fun UpdatedRow(user: UserDetail) {
    InfoRow(GuIcons.Updated, stringResource(R.string.feature_userdetail_updated), relativeTimeText(user.updatedAt))
}

/** 種類などのラベル（角丸 8dp）。 */
@Composable
internal fun Label(text: String, outlined: Boolean = false, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = GuShapes.Chip,
        color = if (outlined) Color.Transparent else colors.secondaryContainer,
        contentColor = if (outlined) colors.onSurfaceVariant else colors.onSecondaryContainer,
        border = if (outlined) androidx.compose.foundation.BorderStroke(1.dp, colors.outline) else null,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** 言語の色の丸と言語名。 */
@Composable
internal fun LanguageTag(language: String) {
    val color = languageColor(language) ?: MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(language, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** アイコン付きの数値（★スター数、フォーク数）。 */
@Composable
internal fun IconCount(icon: ImageVector, count: Int, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(formatCount(count))
    }
}

@Composable
internal fun StarCount(count: Int) =
    IconCount(GuIcons.Star, count, stringResource(R.string.feature_userdetail_stars, formatCount(count)))

@Composable
internal fun ForkCount(count: Int) =
    IconCount(GuIcons.Fork, count, stringResource(R.string.feature_userdetail_forks, formatCount(count)))

/** 人気なリポジトリのカード。 */
@Composable
internal fun PopularRepoCard(
    repo: Repo,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = GuShapes.InnerList,
        color = containerColor,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(GuIcons.Repo, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = repo.description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RepoMeta(repo, showForks = true, showUpdated = false)
        }
    }
}

/** 言語・★・フォーク数・更新日の行（labelMedium）。 */
@Composable
internal fun RepoMeta(repo: Repo, showForks: Boolean, showUpdated: Boolean) {
    val style = MaterialTheme.typography.labelMedium
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
        androidx.compose.material3.LocalTextStyle provides style,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            repo.language?.let { LanguageTag(it) }
            StarCount(repo.stargazersCount)
            if (showForks) ForkCount(repo.forksCount)
            if (showUpdated) {
                Text(stringResource(R.string.feature_userdetail_repo_updated, relativeTimeText(repo.updatedAt)))
            }
        }
    }
}

/** リポジトリ一覧の行。 */
@Composable
internal fun RepoRow(repo: Repo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(repo.name, style = MaterialTheme.typography.titleMedium)
        repo.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(2.dp))
        RepoMeta(repo, showForks = false, showUpdated = true)
    }
}

/** 並び替えの FilterChip。 */
@Composable
internal fun SortChips(
    selected: RepoSort,
    onSelect: (RepoSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortLabel = stringResource(R.string.feature_userdetail_sort)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.semantics { contentDescription = sortLabel },
    ) {
        RepoSort.entries.forEach { sort ->
            val isSelected = sort == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(sort) },
                label = { Text(stringResource(sort.labelRes)) },
                leadingIcon = if (isSelected) {
                    { Icon(GuIcons.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

private val RepoSort.labelRes: Int
    get() = when (this) {
        RepoSort.Updated -> R.string.feature_userdetail_sort_updated
        RepoSort.Stars -> R.string.feature_userdetail_sort_stars
        RepoSort.Name -> R.string.feature_userdetail_sort_name
    }

/** リポジトリ一覧の末尾（さらに表示・読み込み中・失敗）。 */
@Composable
internal fun RepoListFooter(
    state: RepoListState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        when {
            state.isLoading -> androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(24.dp),
                strokeWidth = 3.dp,
            )
            state.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_userdetail_repos_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.feature_userdetail_retry)) }
            }
            state.repos.isEmpty() -> Text(
                stringResource(R.string.feature_userdetail_no_repos),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            state.canLoadMore -> TextButton(onClick = onLoadMore) {
                Text(stringResource(R.string.feature_userdetail_show_more))
            }
        }
    }
}
