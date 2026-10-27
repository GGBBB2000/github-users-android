package com.example.githubusers.core.model

import java.time.LocalDate

/**
 * 過去1年間のコントリビューションのカレンダー（GitHub の「草」）。
 *
 * [weeks] は古い週から順に並ぶ。最初と最後の週は7日に満たないことがある。
 */
data class ContributionCalendar(
    val totalContributions: Int,
    val weeks: List<ContributionWeek>,
) {
    val days: List<ContributionDay> get() = weeks.flatMap { it.days }

    /** 最も件数の多い日。0 件しかなければ null。 */
    val busiestDay: ContributionDay? get() = days.filter { it.count > 0 }.maxByOrNull { it.count }
}

data class ContributionWeek(val days: List<ContributionDay>)

data class ContributionDay(
    val date: LocalDate,
    /** 曜日。0 が日曜日 */
    val weekday: Int,
    val count: Int,
    val level: ContributionLevel,
)

/** 日ごとの濃さ。GitHub が四分位で決めた5段階。 */
enum class ContributionLevel {
    None,
    FirstQuartile,
    SecondQuartile,
    ThirdQuartile,
    FourthQuartile,
}
