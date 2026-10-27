package com.example.githubusers.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 「3日前」のような相対時間の単位と値。 */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val value: Int) : RelativeTime
    data class Hours(val value: Int) : RelativeTime
    data class Days(val value: Int) : RelativeTime
    data class Months(val value: Int) : RelativeTime
    data class Years(val value: Int) : RelativeTime

    companion object {
        /** [time] が [now] からどれだけ前かを、一番大きな単位で表す。未来の時刻は「たった今」とする。 */
        fun between(time: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): RelativeTime {
            val duration = Duration.between(time, now)
            if (duration.toMinutes() < 1) return JustNow
            if (duration.toHours() < 1) return Minutes(duration.toMinutes().toInt())
            if (duration.toDays() < 1) return Hours(duration.toHours().toInt())
            val period = Period.between(time.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
            return when {
                period.years > 0 -> Years(period.years)
                period.months > 0 -> Months(period.months)
                else -> Days(maxOf(period.days, 1))
            }
        }
    }
}

@Composable
fun relativeTimeText(time: Instant, now: Instant = Instant.now()): String =
    when (val relative = RelativeTime.between(time, now)) {
        RelativeTime.JustNow -> stringResource(R.string.core_ui_just_now)
        is RelativeTime.Minutes -> pluralStringResource(R.plurals.core_ui_minutes_ago, relative.value, relative.value)
        is RelativeTime.Hours -> pluralStringResource(R.plurals.core_ui_hours_ago, relative.value, relative.value)
        is RelativeTime.Days -> pluralStringResource(R.plurals.core_ui_days_ago, relative.value, relative.value)
        is RelativeTime.Months -> pluralStringResource(R.plurals.core_ui_months_ago, relative.value, relative.value)
        is RelativeTime.Years -> pluralStringResource(R.plurals.core_ui_years_ago, relative.value, relative.value)
    }

/**
 * 「2007年10月20日」のような日付。
 *
 * 画面の文言と言語を揃えるため、端末のロケールではなく文字列リソースの書式を使う。
 */
@Composable
fun dateText(time: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val pattern = stringResource(R.string.core_ui_date_pattern)
    return DateTimeFormatter.ofPattern(pattern).format(time.atZone(zone))
}

/** 「2007年10月20日（19年前）」のような日付。 */
@Composable
fun dateWithRelativeText(time: Instant, now: Instant = Instant.now()): String =
    stringResource(R.string.core_ui_date_with_relative, dateText(time), relativeTimeText(time, now))
