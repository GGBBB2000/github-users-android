package com.example.githubusers.feature.userdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.githubusers.core.designsystem.component.SkeletonBlock
import com.example.githubusers.core.designsystem.icon.GuIcons
import com.example.githubusers.core.designsystem.theme.GitHubUsersTheme
import com.example.githubusers.core.model.ContributionCalendar
import com.example.githubusers.core.model.ContributionDay
import com.example.githubusers.core.model.ContributionLevel
import com.example.githubusers.core.model.ContributionWeek
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min

/** 曜日のラベルの列の幅。 */
private val WeekdayColumnWidth = 20.dp

/** マスの間隔。 */
private val CellGap = 3.dp

/** 全体を表示できる1列の幅の最小値。これより狭くなるなら横スクロールにする。 */
private val MinFitPitch = 13.dp

/** 横スクロールのときの1列の幅（マス 11dp と間隔 3dp）。 */
private val ScrollPitch = 14.dp
private val MaxCellSize = 13.dp

/** 読み込み中に描く列の数（1年分）。 */
private const val PlaceholderWeeks = 53

/**
 * 草（コントリビューションのカレンダー）のセクション。[ContributionState.Hidden] なら何も出さない。
 *
 * @param isTablet タブレット用のレイアウト（カードの背景とサインインを勧める表示の並べ方が変わる）
 */
@Composable
internal fun ContributionSection(
    state: ContributionState,
    isTablet: Boolean,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == ContributionState.Hidden) return
    val colors = MaterialTheme.colorScheme
    // タブレットはペイン内の他のカードと同じ surface、スマホはアクティビティのリストと同じ色
    val cardColor = if (isTablet) colors.surface else colors.surfaceContainerLow
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            ContributionState.Loading -> CalendarContent(calendar = null, cardColor = cardColor)
            is ContributionState.Success -> CalendarContent(calendar = state.calendar, cardColor = cardColor)
            ContributionState.SignedOut -> SignInPrompt(isTablet = isTablet, onSignIn = onSignIn)
            is ContributionState.Failed -> FailedRow(onRetry = onRetry)
            ContributionState.Hidden -> Unit
        }
    }
}

/** 見出し、草、下の行（選択中の日と凡例）。[calendar] が null なら読み込み中。 */
@Composable
private fun CalendarContent(calendar: ContributionCalendar?, cardColor: Color) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    Header(total = calendar?.totalContributions?.let(numberFormat::format))

    val isEmpty = calendar != null && calendar.totalContributions == 0
    val interactive = calendar != null && !isEmpty
    var selectedText by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedText?.let(LocalDate::parse)?.takeIf { interactive }
    val selectedDay = selected?.let { date -> calendar?.days?.firstOrNull { it.date == date } }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val layout = calendarLayout(
            areaWidth = maxWidth - WeekdayColumnWidth,
            columns = calendar?.weeks?.size ?: PlaceholderWeeks,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CalendarGraph(
                calendar = calendar,
                layout = layout,
                cardColor = cardColor,
                selected = selected,
                onSelect = { date -> selectedText = date?.toString() },
                interactive = interactive,
            )
            Footer(
                calendar = calendar,
                selectedDay = selectedDay,
                isEmpty = isEmpty,
                cellSize = layout.cell,
                cardColor = cardColor,
            )
        }
    }
}

@Composable
private fun Header(total: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (total == null) {
            SkeletonBlock(Modifier.size(200.dp, 14.dp), RoundedCornerShape(7.dp))
            Spacer(Modifier.weight(1f))
            SkeletonBlock(Modifier.size(56.dp, 14.dp), RoundedCornerShape(7.dp))
        } else {
            Text(
                text = stringResource(R.string.feature_userdetail_contrib_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.feature_userdetail_contrib_total, total),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** マスの大きさと、全体を表示できるか（できなければ横スクロール）。 */
internal data class CalendarLayout(val pitch: Dp, val cell: Dp, val fits: Boolean)

/**
 * マスの領域の幅 [areaWidth] から、マスの大きさを決める。
 *
 * 1列あたり 13dp 以上取れるなら全体を表示し、取れなければ 1列 14dp の横スクロールにする。
 */
internal fun calendarLayout(areaWidth: Dp, columns: Int): CalendarLayout {
    val fitPitch = floor(areaWidth.value / columns.coerceAtLeast(1)).dp
    val fits = fitPitch >= MinFitPitch
    val pitch = if (fits) fitPitch else ScrollPitch
    return CalendarLayout(pitch = pitch, cell = min((pitch - CellGap).value, MaxCellSize.value).dp, fits = fits)
}

/**
 * 月のラベルを置く列と月（1〜12）。
 *
 * 1日を含む週の列に置く。最初の列には、途中から始まる月のラベルを置く。
 * 次のラベルまで3列未満なら、重ならないように省く。
 */
internal fun monthLabels(weeks: List<ContributionWeek>): List<Pair<Int, Int>> {
    val labels = mutableListOf<Pair<Int, Int>>()
    weeks.forEachIndexed { column, week ->
        val first = week.days.firstOrNull() ?: return@forEachIndexed
        // 最初の列の、途中から始まる月
        if (column == 0 && first.date.dayOfMonth != 1) labels += column to first.date.monthValue
        week.days.firstOrNull { it.date.dayOfMonth == 1 }?.let { labels += column to it.date.monthValue }
    }
    return labels.filterIndexed { i, (column, _) -> i == labels.lastIndex || labels[i + 1].first - column >= 3 }
}

/** [column] 列目の [weekday] の日。ない日（最初と最後の週の欠け）なら null。 */
internal fun dayAt(weeks: List<ContributionWeek>, column: Int, weekday: Int): ContributionDay? =
    weeks.getOrNull(column)?.days?.firstOrNull { it.weekday == weekday }

/**
 * TalkBack の操作で選択を [offsetDays] 日動かす。選んでいなければ最新の日を選ぶ。
 * 範囲の外に出るなら動かさない。
 */
internal fun moveSelection(days: List<ContributionDay>, selected: LocalDate?, offsetDays: Long): LocalDate? {
    if (days.isEmpty()) return null
    if (selected == null) return days.last().date
    val target = selected.plusDays(offsetDays)
    return if (days.any { it.date == target }) target else selected
}

@Composable
private fun CalendarGraph(
    calendar: ContributionCalendar?,
    layout: CalendarLayout,
    cardColor: Color,
    selected: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
    interactive: Boolean,
) {
    val weeks = calendar?.weeks.orEmpty()
    val columns = if (calendar == null) PlaceholderWeeks else weeks.size
    val gridWidth = layout.pitch * columns - (layout.pitch - layout.cell)
    val gridHeight = layout.pitch * 7 - (layout.pitch - layout.cell)
    val labelAlpha = if (calendar == null) 0f else 1f
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scrollState = rememberScrollState()

    // 横スクロールのときは、最初に右端（最近の週）を表示する。回転などで戻ったときは位置を保つ
    LaunchedEffect(layout.fits, columns) {
        if (!layout.fits && scrollState.value == 0) {
            snapshotFlow { scrollState.maxValue }.first { it > 0 && it < Int.MAX_VALUE }
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Row(Modifier.fillMaxWidth().calendarSemantics(calendar, selected, onSelect)) {
        // 曜日のラベルは横スクロールしない
        Column(Modifier.width(WeekdayColumnWidth).alpha(labelAlpha)) {
            // 月のラベルの行と同じ高さにする（文字の大きさを変えても揃うように、同じ文字の大きさで場所を取る）
            Text("", style = labelStyle, modifier = Modifier.heightIn(min = 16.dp))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.height(gridHeight)) {
                WeekdayLabels.forEach { (weekday, label) ->
                    // その行のマスの中央にそろえる
                    Box(
                        modifier = Modifier.offset(y = layout.pitch * weekday).height(layout.cell),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = stringResource(label),
                            style = labelStyle,
                            color = labelColor,
                            modifier = Modifier.wrapContentHeight(unbounded = true),
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            Column(if (layout.fits) Modifier else Modifier.horizontalScroll(scrollState)) {
                Box(Modifier.width(gridWidth).heightIn(min = 16.dp).alpha(labelAlpha)) {
                    monthLabels(weeks).forEach { (column, month) ->
                        Text(
                            text = stringResource(R.string.feature_userdetail_contrib_month, month),
                            style = labelStyle,
                            color = labelColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.offset(x = layout.pitch * column),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                CalendarCells(
                    weeks = weeks,
                    placeholderColumns = if (calendar == null) PlaceholderWeeks else 0,
                    layout = layout,
                    cardColor = cardColor,
                    selected = selected,
                    onSelect = onSelect,
                    interactive = interactive,
                    modifier = Modifier.size(gridWidth, gridHeight),
                )
            }
            if (!layout.fits && scrollState.canScrollBackward) {
                // 左に続きがあることを示す
                val fadeWidth = with(LocalDensity.current) { 24.dp.toPx() }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Brush.horizontalGradient(listOf(cardColor, Color.Transparent), endX = fadeWidth)),
                )
            }
        }
    }
}

private val WeekdayLabels = listOf(
    1 to R.string.feature_userdetail_contrib_monday,
    3 to R.string.feature_userdetail_contrib_wednesday,
    5 to R.string.feature_userdetail_contrib_friday,
)

@Composable
private fun CalendarCells(
    weeks: List<ContributionWeek>,
    placeholderColumns: Int,
    layout: CalendarLayout,
    cardColor: Color,
    selected: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val levelColors = levelColors(cardColor)
    val outline = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val pitchPx = with(density) { layout.pitch.toPx() }
    Canvas(
        modifier.pointerInput(weeks, interactive, pitchPx, selected) {
            if (!interactive) return@pointerInput
            // マス1つずつはタップの最小サイズに満たないので、領域全体で受けて指の位置から日を決める
            detectTapGestures { position ->
                val column = (position.x / pitchPx).toInt().coerceIn(0, weeks.lastIndex)
                val weekday = (position.y / pitchPx).toInt().coerceIn(0, 6)
                val day = dayAt(weeks, column, weekday) ?: return@detectTapGestures
                onSelect(if (day.date == selected) null else day.date)
            }
        },
    ) {
        val cell = layout.cell.toPx()
        val radius = CornerRadius(2.dp.toPx())
        if (placeholderColumns > 0) {
            for (column in 0 until placeholderColumns) {
                for (weekday in 0 until 7) {
                    drawRoundRect(levelColors.first(), Offset(column * pitchPx, weekday * pitchPx), Size(cell, cell), radius)
                }
            }
            return@Canvas
        }
        weeks.forEachIndexed { column, week ->
            week.days.forEach { day ->
                val topLeft = Offset(column * pitchPx, day.weekday * pitchPx)
                drawRoundRect(levelColors[day.level.ordinal], topLeft, Size(cell, cell), radius)
                if (day.date == selected) {
                    // 色だけに頼らず、枠でも選んだ日を示す（マスの外側に 2dp）
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        color = outline,
                        topLeft = topLeft - Offset(stroke / 2, stroke / 2),
                        size = Size(cell + stroke, cell + stroke),
                        cornerRadius = CornerRadius(radius.x + stroke / 2),
                        style = Stroke(stroke),
                    )
                }
            }
        }
    }
}

/**
 * 5段階の色。`primary` の不透明度で濃さを表し、カードの背景に重ねて不透明な色にする。
 *
 * ロールの組み合わせ（`primaryContainer` など）はライトとダークで明るさの順番が逆になるものがあり、
 * 「濃い＝多い」が保てないため、`primary` の不透明度にする。
 */
@Composable
private fun levelColors(cardColor: Color): List<Color> {
    val colors = MaterialTheme.colorScheme
    return listOf(
        colors.surfaceContainerHighest,
        colors.primary.copy(alpha = 0.30f).compositeOver(cardColor),
        colors.primary.copy(alpha = 0.52f).compositeOver(cardColor),
        colors.primary.copy(alpha = 0.76f).compositeOver(cardColor),
        colors.primary,
    ).also { check(it.size == ContributionLevel.entries.size) }
}

/** 草全体を1つの要素にして、要約を読み上げる。TalkBack の操作で選択を動かせる。 */
@Composable
private fun Modifier.calendarSemantics(
    calendar: ContributionCalendar?,
    selected: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
): Modifier {
    if (calendar == null) {
        val loading = stringResource(R.string.feature_userdetail_contrib_loading)
        return clearAndSetSemantics { contentDescription = loading }
    }
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val total = numberFormat.format(calendar.totalContributions)
    val busiest = calendar.busiestDay
    val datePattern = stringResource(R.string.feature_userdetail_contrib_summary_date_pattern)
    val summary = if (busiest == null) {
        stringResource(R.string.feature_userdetail_contrib_summary, total)
    } else {
        stringResource(
            R.string.feature_userdetail_contrib_summary_busiest,
            total,
            DateTimeFormatter.ofPattern(datePattern, Locale.JAPANESE).format(busiest.date),
            numberFormat.format(busiest.count),
        )
    }
    if (calendar.totalContributions == 0) return clearAndSetSemantics { contentDescription = summary }
    val days = calendar.days
    val actions = listOf(
        R.string.feature_userdetail_contrib_prev_day to -1L,
        R.string.feature_userdetail_contrib_next_day to 1L,
        R.string.feature_userdetail_contrib_prev_week to -7L,
        R.string.feature_userdetail_contrib_next_week to 7L,
    ).map { (label, offset) ->
        CustomAccessibilityAction(stringResource(label)) {
            onSelect(moveSelection(days, selected, offset))
            true
        }
    }
    return clearAndSetSemantics {
        contentDescription = summary
        customActions = actions
    }
}

@Composable
private fun Footer(
    calendar: ContributionCalendar?,
    selectedDay: ContributionDay?,
    isEmpty: Boolean,
    cellSize: Dp,
    cardColor: Color,
) {
    val colors = MaterialTheme.colorScheme
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val textModifier = Modifier.weight(1f)
        when {
            calendar == null -> Spacer(textModifier)
            isEmpty -> FooterText(stringResource(R.string.feature_userdetail_contrib_empty), textModifier)
            selectedDay != null -> {
                val count = numberFormat.format(selectedDay.count)
                val shortDate = DateTimeFormatter
                    .ofPattern(stringResource(R.string.feature_userdetail_contrib_date_pattern), Locale.JAPANESE)
                    .format(selectedDay.date)
                val spokenDate = DateTimeFormatter
                    .ofPattern(stringResource(R.string.feature_userdetail_contrib_date_spoken_pattern), Locale.JAPANESE)
                    .format(selectedDay.date)
                val spoken = stringResource(R.string.feature_userdetail_contrib_selected_spoken, spokenDate, count)
                Text(
                    text = stringResource(R.string.feature_userdetail_contrib_selected, shortDate, count),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    // 選んだ日が変わったら読み上げる。画面の表示より長い形で読む
                    modifier = textModifier.semantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
            else -> FooterText(stringResource(R.string.feature_userdetail_contrib_hint), textModifier)
        }
        if (calendar != null && !isEmpty) Legend(cellSize, cardColor)
    }
}

@Composable
private fun FooterText(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun Legend(cellSize: Dp, cardColor: Color) {
    val description = stringResource(R.string.feature_userdetail_contrib_legend)
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(stringResource(R.string.feature_userdetail_contrib_less), style = style, color = color)
        Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            levelColors(cardColor).forEach { levelColor ->
                Box(Modifier.size(cellSize).background(levelColor, RoundedCornerShape(2.dp)))
            }
        }
        Text(stringResource(R.string.feature_userdetail_contrib_more), style = style, color = color)
    }
}

@Composable
private fun SignInPrompt(isTablet: Boolean, onSignIn: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val message = @Composable { modifier: Modifier ->
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GuIcons.Contributions, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.feature_userdetail_contrib_signed_out_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.feature_userdetail_contrib_signed_out_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
    val button = @Composable { modifier: Modifier ->
        FilledTonalButton(
            onClick = onSignIn,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = modifier,
        ) {
            Icon(GuIcons.SignIn, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.feature_userdetail_contrib_sign_in))
        }
    }
    if (isTablet) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            message(Modifier.weight(1f))
            button(Modifier)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            message(Modifier)
            button(Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun FailedRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(GuIcons.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
        Text(
            stringResource(R.string.feature_userdetail_contrib_failed),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.feature_userdetail_retry)) }
    }
}

private val previewCalendar = run {
    val end = LocalDate.of(2026, 10, 28)
    val lastSunday = end.minusDays((end.dayOfWeek.value % 7).toLong())
    val weeks = (52 downTo 0).map { w ->
        val sunday = lastSunday.minusWeeks(w.toLong())
        ContributionWeek(
            (0 until 7).map { sunday.plusDays(it.toLong()) }.filter { !it.isAfter(end) }.map { date ->
                val count = (date.dayOfYear * 7 + date.dayOfMonth) % 13
                ContributionDay(date, date.dayOfWeek.value % 7, count, ContributionLevel.entries[min(count / 3, 4)])
            },
        )
    }
    ContributionCalendar(weeks.sumOf { w -> w.days.sumOf { it.count } }, weeks)
}

@Preview(widthDp = 380)
@Composable
private fun ContributionSectionPreview() {
    GitHubUsersTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ContributionSection(ContributionState.Success(previewCalendar), isTablet = false, onSignIn = {}, onRetry = {})
            ContributionSection(ContributionState.Loading, isTablet = false, onSignIn = {}, onRetry = {})
            ContributionSection(ContributionState.SignedOut, isTablet = false, onSignIn = {}, onRetry = {})
            ContributionSection(
                ContributionState.Failed(com.example.githubusers.core.model.GitHubException.Network(java.io.IOException())),
                isTablet = false,
                onSignIn = {},
                onRetry = {},
            )
        }
    }
}
