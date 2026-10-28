package com.example.githubusers.core.data.summary

import com.example.githubusers.core.model.ContributionCalendar
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.SummaryFact
import com.example.githubusers.core.model.SummaryFactKind
import com.example.githubusers.core.model.UserDetail
import com.example.githubusers.core.model.formatCompactCount
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 要約のもとにするデータ。詳細画面で読み込み済みのものを使う（要約のために API を呼び直さない）。
 *
 * @param calendar 草。未ログイン、Organization、取得に失敗したときは null
 * @param repos 読み込み済みのリポジトリ一覧（よく使う言語を数える）
 * @param popularRepos スター数の多い順のリポジトリ
 */
data class ActivitySummaryInput(
    val user: UserDetail,
    val calendar: ContributionCalendar?,
    val repos: List<Repo>,
    val popularRepos: List<Repo>,
)

/** 要約できるほどの活動があるか。公開リポジトリがなく、過去1年間のコントリビューションも少ないなら、要約しない。 */
fun hasEnoughActivityForSummary(publicRepos: Int, totalContributions: Int?): Boolean =
    publicRepos > 0 || (totalContributions ?: 0) >= MIN_CONTRIBUTIONS_WITHOUT_REPOS

private const val MIN_CONTRIBUTIONS_WITHOUT_REPOS = 10
private const val TOP_LANGUAGES = 3
private val WEEKDAYS = listOf("日曜日", "月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日")

/**
 * 要約のもとにする値を計算して、表示する形に整える。値がないもの（0 日の連続など）は含めない。
 *
 * 値の表記は画面とそろえる（`24.1k`、`1,269 件` など）。AI にも同じ表記を渡し、出力の数値と照らし合わせる。
 *
 * @param today 登録してからの年数を数える日
 */
internal fun activityFacts(input: ActivitySummaryInput, today: LocalDate): List<SummaryFact> = buildList {
    input.calendar?.let { addAll(contributionFacts(it)) }

    topLanguages(input.repos)?.let { add(SummaryFact(SummaryFactKind.TopLanguages, it)) }
    input.popularRepos.filter { it.stargazersCount > 0 }.maxByOrNull { it.stargazersCount }?.let {
        add(SummaryFact(SummaryFactKind.TopRepo, "${it.name}（★${formatCompactCount(it.stargazersCount)}）"))
    }
    val years = ChronoUnit.YEARS.between(input.user.createdAt.atZone(ZoneOffset.UTC).toLocalDate(), today)
    if (years >= 1) add(SummaryFact(SummaryFactKind.YearsOnGitHub, "$years 年"))
    add(SummaryFact(SummaryFactKind.Followers, formatCompactCount(input.user.followers)))
    add(SummaryFact(SummaryFactKind.PublicRepos, formatCompactCount(input.user.publicRepos)))
}

private fun contributionFacts(calendar: ContributionCalendar): List<SummaryFact> = buildList {
    val days = calendar.days
    add(SummaryFact(SummaryFactKind.Contributions, "${NumberFormat.getIntegerInstance(Locale.JAPAN).format(calendar.totalContributions)} 件"))
    if (calendar.totalContributions == 0) return@buildList

    val activeDays = days.count { it.count > 0 }
    if (activeDays > 0) add(SummaryFact(SummaryFactKind.ActiveDays, "$activeDays 日"))
    val longest = longestStreak(days.map { it.count })
    if (longest > 0) add(SummaryFact(SummaryFactKind.LongestStreak, "$longest 日"))
    val current = currentStreak(days.map { it.count })
    if (current > 0) add(SummaryFact(SummaryFactKind.CurrentStreak, "$current 日"))

    // 同じ件数なら、先に出てくるもの（日曜日に近い曜日、古い月）にする
    val weekday = days.groupBy { it.weekday }.mapValues { (_, d) -> d.sumOf { it.count } }
        .entries.sortedBy { it.key }.maxBy { it.value }.key
    add(SummaryFact(SummaryFactKind.BusiestWeekday, WEEKDAYS[weekday]))
    val month = days.groupBy { YearMonth.from(it.date) }.mapValues { (_, d) -> d.sumOf { it.count } }
        .entries.sortedBy { it.key }.maxBy { it.value }.key
    add(SummaryFact(SummaryFactKind.BusiestMonth, "${month.year}年${month.monthValue}月"))
}

/** 最も長く続いた、件数が 1 以上の日の連続。 */
internal fun longestStreak(counts: List<Int>): Int {
    var longest = 0
    var run = 0
    counts.forEach { count ->
        run = if (count > 0) run + 1 else 0
        longest = maxOf(longest, run)
    }
    return longest
}

/**
 * 最後の日から数えた連続。最後の日（今日）がまだ 0 件なら、前の日から数える（今日の活動がまだないだけで途切れたことにしない）。
 */
internal fun currentStreak(counts: List<Int>): Int {
    val fromEnd = counts.asReversed().let { if (it.firstOrNull() == 0) it.drop(1) else it }
    return fromEnd.takeWhile { it > 0 }.size
}

/** フォークを除いたリポジトリで、多く使われている言語の上位3つ（「Ruby、JavaScript、C」）。 */
private fun topLanguages(repos: List<Repo>): String? =
    repos.filter { !it.isFork }
        .mapNotNull { it.language }
        .groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(TOP_LANGUAGES)
        .joinToString("、") { it.key }
        .ifEmpty { null }
