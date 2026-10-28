package com.example.githubusers.feature.userdetail

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.githubusers.core.designsystem.component.SkeletonBlock
import com.example.githubusers.core.designsystem.icon.GuIcons
import com.example.githubusers.core.designsystem.theme.GitHubUsersTheme
import com.example.githubusers.core.model.ActivitySummary
import com.example.githubusers.core.model.SummaryFact
import com.example.githubusers.core.model.SummaryFactKind

/** 数値の一覧を4列に並べる（タブレット）。 */
private const val FactColumns = 4

/**
 * 端末上の AI による活動の要約。[SummaryState.Hidden] なら何も出さない。
 *
 * @param isTablet タブレット用のレイアウト（カードの背景、数値の一覧の出し方、ダウンロードの案内の並べ方が変わる）
 */
@Composable
internal fun SummarySection(
    state: SummaryState,
    isTablet: Boolean,
    callbacks: SummaryCallbacks,
    modifier: Modifier = Modifier,
) {
    if (state == SummaryState.Hidden) return
    val colors = MaterialTheme.colorScheme
    val cardColor = if (isTablet) colors.surface else colors.surfaceContainerLow
    // 1行だけの表示（入口・ダウンロードの案内・データ不足）は、上下の余白を小さくして場所を取らないようにする
    val compact = state == SummaryState.Entry || state == SummaryState.NeedsDownload ||
        state == SummaryState.NoData || state == SummaryState.DownloadFailed
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(horizontal = 16.dp, vertical = if (compact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            SummaryState.Entry -> CompactRow(
                text = stringResource(R.string.feature_userdetail_summary_entry),
                button = stringResource(R.string.feature_userdetail_summary_create),
                onClick = callbacks.onGenerate,
                isTablet = true,
            )
            SummaryState.NeedsDownload -> CompactRow(
                text = stringResource(R.string.feature_userdetail_summary_needs_download),
                subText = stringResource(R.string.feature_userdetail_summary_download_size),
                button = stringResource(R.string.feature_userdetail_summary_download),
                onClick = callbacks.onDownload,
                isTablet = isTablet,
            )
            SummaryState.NoData -> CompactRow(
                text = stringResource(R.string.feature_userdetail_summary_no_data),
                iconTint = colors.onSurfaceVariant,
                isTablet = true,
            )
            is SummaryState.Downloading -> DownloadingContent(state)
            SummaryState.DownloadFailed -> MessageRow(
                icon = GuIcons.Error,
                iconTint = colors.error,
                text = stringResource(R.string.feature_userdetail_summary_download_failed),
                onRetry = callbacks.onDownload,
            )
            SummaryState.Generating -> {
                Header(onCancel = callbacks.onCancel)
                GeneratingContent()
            }
            is SummaryState.Done -> {
                Header()
                DoneContent(state, isTablet, callbacks)
            }
            is SummaryState.Failed -> {
                Header()
                FailedContent(state.reason, onRetry = callbacks.onGenerate)
            }
            SummaryState.Hidden -> Unit
        }
    }
}

internal data class SummaryCallbacks(
    val onGenerate: () -> Unit,
    val onCancel: () -> Unit,
    val onDownload: () -> Unit,
)

/**
 * 入口・ダウンロードの案内・データ不足の1行。
 *
 * @param isTablet false なら、ボタンを文の下に右寄せで置く（補足の文があって長いとき、スマホの幅で横に並べると窮屈なため）
 */
@Composable
private fun CompactRow(
    text: String,
    isTablet: Boolean,
    subText: String? = null,
    button: String? = null,
    onClick: () -> Unit = {},
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    val message = @Composable { modifier: Modifier ->
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(GuIcons.Ai, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                subText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val action = @Composable { modifier: Modifier ->
        if (button != null) FilledTonalButton(onClick = onClick, modifier = modifier) { Text(button, maxLines = 1) }
    }
    if (isTablet || subText == null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            message(Modifier.weight(1f))
            action(Modifier)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message(Modifier)
            action(Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun DownloadingContent(state: SummaryState.Downloading) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val progressLabel = stringResource(R.string.feature_userdetail_summary_download_progress)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(GuIcons.Download, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Text(
                stringResource(R.string.feature_userdetail_summary_downloading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.downloadedBytes > 0) {
                Text(
                    Formatter.formatShortFileSize(context, state.downloadedBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        val indicatorModifier = Modifier
            .padding(start = 36.dp)
            .fillMaxWidth()
            .semantics { contentDescription = progressLabel }
        val total = state.totalBytes
        if (total != null) {
            LinearProgressIndicator(
                progress = { (state.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                modifier = indicatorModifier,
            )
        } else {
            LinearProgressIndicator(modifier = indicatorModifier)
        }
        Text(
            stringResource(R.string.feature_userdetail_summary_downloading_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 36.dp),
        )
    }
}

/** 見出し「AI による要約」。[onCancel] があれば右端に「中止」を出す。 */
@Composable
private fun Header(onCancel: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(GuIcons.Ai, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.feature_userdetail_summary_title),
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onCancel != null) {
            TextButton(
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = 12.dp),
                // 文字の右端をカードの中身にそろえる
                modifier = Modifier.heightIn(max = 32.dp).offset(x = 8.dp),
            ) {
                Text(stringResource(R.string.feature_userdetail_summary_cancel))
            }
        }
    }
}

@Composable
private fun GeneratingContent() {
    Column(Modifier.padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(14.dp), shape = RoundedCornerShape(7.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(14.dp), shape = RoundedCornerShape(7.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.62f).height(14.dp), shape = RoundedCornerShape(7.dp))
    }
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.feature_userdetail_summary_generating),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DoneContent(state: SummaryState.Done, isTablet: Boolean, callbacks: SummaryCallbacks) {
    val colors = MaterialTheme.colorScheme
    val summary = state.summary
    val spoken = stringResource(R.string.feature_userdetail_summary_spoken, summary.text)
    // ストリーミングしないので、出来上がったときに1回だけ読み上げる
    Text(
        summary.text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
    )
    if (isTablet) FactGrid(summary.facts) else FactList(summary.facts)
    if (state.canIncludeContributions) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.secondaryContainer)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.feature_userdetail_summary_include_contributions),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            TextButton(onClick = callbacks.onGenerate) {
                Text(stringResource(R.string.feature_userdetail_summary_rebuild))
            }
        }
    }
    val regenerateDescription = stringResource(R.string.feature_userdetail_summary_regenerate_description)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.feature_userdetail_summary_note),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        // 回数の上限があるので、目立たない TextButton にする
        TextButton(
            onClick = callbacks.onGenerate,
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            modifier = Modifier.offset(x = 12.dp).semantics { contentDescription = regenerateDescription },
        ) {
            Icon(GuIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.feature_userdetail_summary_regenerate))
        }
    }
}

/** スマホ：「もとにした数値（11）」で開閉する一覧。初めは閉じる。 */
@Composable
private fun FactList(facts: List<SummaryFact>) {
    val colors = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = { open = !open },
            contentPadding = PaddingValues(horizontal = 12.dp),
            // 文字の左端をカードの中身にそろえる
            modifier = Modifier.offset(x = (-12).dp).semantics {
                // TalkBack で「展開」「折りたたみ」の状態を伝える
                if (open) collapse { open = false; true } else expand { open = true; true }
            },
        ) {
            Text(stringResource(R.string.feature_userdetail_summary_facts, facts.size))
            Spacer(Modifier.width(4.dp))
            Icon(GuIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp).rotate(rotation))
        }
        AnimatedVisibility(visible = open) {
            Surface(color = colors.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    facts.forEachIndexed { index, fact ->
                        if (index > 0) HorizontalDivider(color = colors.surfaceContainerHighest)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                stringResource(fact.kind.labelRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                            Text(
                                fact.value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** タブレット：区切り線の下に4列で常に表示する。 */
@Composable
private fun FactGrid(facts: List<SummaryFact>) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(color = colors.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            facts.chunked(FactColumns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { fact ->
                        Column(
                            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {},
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                stringResource(fact.kind.labelRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                            Text(fact.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    repeat(FactColumns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FailedContent(reason: SummaryFailure, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    when (reason) {
        SummaryFailure.Busy -> MessageRow(
            icon = GuIcons.Busy,
            iconTint = colors.onSurfaceVariant,
            text = stringResource(R.string.feature_userdetail_summary_busy),
            subText = stringResource(R.string.feature_userdetail_summary_busy_message),
            onRetry = onRetry,
        )
        // 明日まで作れないので、再試行は出さない
        SummaryFailure.QuotaExceeded -> MessageRow(
            icon = GuIcons.Info,
            iconTint = colors.onSurfaceVariant,
            text = stringResource(R.string.feature_userdetail_summary_quota),
            subText = stringResource(R.string.feature_userdetail_summary_quota_message),
        )
        SummaryFailure.Other -> MessageRow(
            icon = GuIcons.Error,
            iconTint = colors.error,
            text = stringResource(R.string.feature_userdetail_summary_failed),
            onRetry = onRetry,
        )
    }
}

@Composable
private fun MessageRow(
    icon: ImageVector,
    iconTint: Color,
    text: String,
    subText: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Column(
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            subText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.feature_userdetail_retry)) }
        }
    }
}

@get:StringRes
private val SummaryFactKind.labelRes: Int
    get() = when (this) {
        SummaryFactKind.Contributions -> R.string.feature_userdetail_summary_fact_contributions
        SummaryFactKind.ActiveDays -> R.string.feature_userdetail_summary_fact_active_days
        SummaryFactKind.LongestStreak -> R.string.feature_userdetail_summary_fact_longest_streak
        SummaryFactKind.CurrentStreak -> R.string.feature_userdetail_summary_fact_current_streak
        SummaryFactKind.BusiestWeekday -> R.string.feature_userdetail_summary_fact_busiest_weekday
        SummaryFactKind.BusiestMonth -> R.string.feature_userdetail_summary_fact_busiest_month
        SummaryFactKind.TopLanguages -> R.string.feature_userdetail_summary_fact_top_languages
        SummaryFactKind.TopRepo -> R.string.feature_userdetail_summary_fact_top_repo
        SummaryFactKind.YearsOnGitHub -> R.string.feature_userdetail_summary_fact_years
        SummaryFactKind.Followers -> R.string.feature_userdetail_summary_fact_followers
        SummaryFactKind.PublicRepos -> R.string.feature_userdetail_summary_fact_public_repos
    }

private val previewSummary = ActivitySummary(
    login = "octocat",
    text = "過去1年間で 1,269 件のコントリビューションがあり、特に木曜日に活発です。" +
        "Ruby を中心に JavaScript や C も使い、最も人気のあるリポジトリは chronic です。2026年5月が最も活動の多い月でした。",
    facts = listOf(
        SummaryFact(SummaryFactKind.Contributions, "1,269 件"),
        SummaryFact(SummaryFactKind.ActiveDays, "247 日"),
        SummaryFact(SummaryFactKind.BusiestWeekday, "木曜日"),
        SummaryFact(SummaryFactKind.TopLanguages, "Ruby、JavaScript、C"),
        SummaryFact(SummaryFactKind.Followers, "24.1k"),
    ),
    includesContributions = true,
)

@Preview(widthDp = 380)
@Composable
private fun SummarySectionPreview() {
    val callbacks = SummaryCallbacks({}, {}, {})
    GitHubUsersTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SummarySection(SummaryState.Entry, isTablet = false, callbacks = callbacks)
            SummarySection(SummaryState.NeedsDownload, isTablet = false, callbacks = callbacks)
            SummarySection(SummaryState.Downloading(412_000_000, 712_000_000), isTablet = false, callbacks = callbacks)
            SummarySection(SummaryState.Generating, isTablet = false, callbacks = callbacks)
            SummarySection(SummaryState.Done(previewSummary, canIncludeContributions = false), isTablet = false, callbacks = callbacks)
            SummarySection(SummaryState.Failed(SummaryFailure.QuotaExceeded), isTablet = false, callbacks = callbacks)
        }
    }
}
