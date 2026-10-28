package com.example.githubusers.core.data.summary

import com.example.githubusers.core.model.AccountType
import com.example.githubusers.core.model.ContributionCalendar
import com.example.githubusers.core.model.ContributionDay
import com.example.githubusers.core.model.ContributionLevel
import com.example.githubusers.core.model.ContributionWeek
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.SummaryFactKind
import com.example.githubusers.core.model.UserDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ActivityFactsTest {
    private val today = LocalDate.parse("2026-10-28")

    private val user = UserDetail(
        id = 1, login = "mojombo", avatarUrl = "", htmlUrl = "", type = AccountType.User,
        name = "Tom", company = null, blog = null, location = null, email = null, bio = null, twitterUsername = null,
        publicRepos = 66, publicGists = 0, followers = 24_130, following = 0,
        createdAt = Instant.parse("2007-10-20T05:24:19Z"), updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private fun repo(name: String, language: String?, stars: Int = 0, isFork: Boolean = false) = Repo(
        id = name.hashCode().toLong(), name = name, description = null, htmlUrl = "", language = language,
        stargazersCount = stars, forksCount = 0, updatedAt = Instant.EPOCH, isFork = isFork,
    )

    /** [start] から1日ずつ [counts] の件数を並べたカレンダー。 */
    private fun calendar(start: LocalDate, counts: List<Int>): ContributionCalendar {
        val days = counts.mapIndexed { i, count ->
            val date = start.plusDays(i.toLong())
            ContributionDay(date, date.dayOfWeek.value % 7, count, if (count > 0) ContributionLevel.FirstQuartile else ContributionLevel.None)
        }
        return ContributionCalendar(counts.sum(), days.chunked(7).map { ContributionWeek(it) })
    }

    private fun facts(
        calendar: ContributionCalendar? = null,
        repos: List<Repo> = emptyList(),
        popular: List<Repo> = emptyList(),
    ) = activityFacts(ActivitySummaryInput(user, calendar, repos, popular), today).associate { it.kind to it.value }

    @Test
    fun `草・リポジトリ・プロフィールから値を作り、画面と同じ表記にする`() {
        // 2026-10-19（月）から10日分。木曜日（10/22）に最も多い
        val calendar = calendar(LocalDate.parse("2026-10-19"), listOf(1, 2, 0, 1200, 30, 0, 1, 1, 1, 0))
        val facts = facts(
            calendar = calendar,
            repos = listOf(repo("a", "Ruby"), repo("b", "Ruby"), repo("c", "C"), repo("d", "JavaScript"), repo("e", "JavaScript"), repo("f", "Go")),
            popular = listOf(repo("chronic", "Ruby", stars = 3_212), repo("god", "Ruby", stars = 2_000)),
        )

        assertEquals("1,236 件", facts[SummaryFactKind.Contributions])
        assertEquals("7 日", facts[SummaryFactKind.ActiveDays])
        assertEquals("3 日", facts[SummaryFactKind.LongestStreak])
        // 最後の日が 0 件なので、前の日から数える
        assertEquals("3 日", facts[SummaryFactKind.CurrentStreak])
        assertEquals("木曜日", facts[SummaryFactKind.BusiestWeekday])
        assertEquals("2026年10月", facts[SummaryFactKind.BusiestMonth])
        // 同じ数なら名前順
        assertEquals("JavaScript、Ruby、C", facts[SummaryFactKind.TopLanguages])
        assertEquals("chronic（★3.2k）", facts[SummaryFactKind.TopRepo])
        assertEquals("19 年", facts[SummaryFactKind.YearsOnGitHub])
        assertEquals("24.1k", facts[SummaryFactKind.Followers])
        assertEquals("66", facts[SummaryFactKind.PublicRepos])
    }

    @Test
    fun `値は画面に並べる順番で返す`() {
        val calendar = calendar(LocalDate.parse("2026-10-19"), listOf(1, 1))
        val kinds = activityFacts(ActivitySummaryInput(user, calendar, listOf(repo("a", "Go")), listOf(repo("a", "Go", 5))), today)
            .map { it.kind }

        assertEquals(kinds.sortedBy { it.ordinal }, kinds)
    }

    @Test
    fun `草がなければ、草から作る値を含めない`() {
        val facts = facts(calendar = null)

        assertTrue(SummaryFactKind.entries.filter { it.fromContributions }.none { it in facts })
        assertEquals("24.1k", facts[SummaryFactKind.Followers])
    }

    @Test
    fun `過去1年間が 0 件なら、合計だけを出す`() {
        val facts = facts(calendar = calendar(LocalDate.parse("2026-10-19"), listOf(0, 0, 0)))

        assertEquals("0 件", facts[SummaryFactKind.Contributions])
        assertEquals(listOf(SummaryFactKind.Contributions), facts.keys.filter { it.fromContributions })
    }

    @Test
    fun `フォークは言語に数えず、スターのないリポジトリは最も人気のあるリポジトリにしない`() {
        val facts = facts(
            repos = listOf(repo("fork", "Rust", isFork = true), repo("none", null)),
            popular = listOf(repo("zero", "Go", stars = 0)),
        )

        assertNull(facts[SummaryFactKind.TopLanguages])
        assertNull(facts[SummaryFactKind.TopRepo])
    }

    @Test
    fun `連続の日数`() {
        assertEquals(3, longestStreak(listOf(1, 1, 0, 1, 1, 1, 0)))
        assertEquals(0, longestStreak(listOf(0, 0)))
        assertEquals(2, currentStreak(listOf(0, 1, 1)))
        // 今日がまだ 0 件なら、昨日まで続いていれば途切れていない
        assertEquals(2, currentStreak(listOf(0, 1, 1, 0)))
        assertEquals(0, currentStreak(listOf(1, 0, 0)))
    }

    @Test
    fun `要約できるほどの活動があるか`() {
        assertTrue(hasEnoughActivityForSummary(publicRepos = 1, totalContributions = null))
        assertTrue(hasEnoughActivityForSummary(publicRepos = 0, totalContributions = 10))
        assertFalse(hasEnoughActivityForSummary(publicRepos = 0, totalContributions = 9))
        assertFalse(hasEnoughActivityForSummary(publicRepos = 0, totalContributions = null))
    }
}
