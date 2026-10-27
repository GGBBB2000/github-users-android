package com.example.githubusers.core.testing

import com.example.githubusers.core.model.AccountType
import com.example.githubusers.core.model.ContributionCalendar
import com.example.githubusers.core.model.ContributionDay
import com.example.githubusers.core.model.ContributionLevel
import com.example.githubusers.core.model.ContributionWeek
import com.example.githubusers.core.model.DeviceCode
import com.example.githubusers.core.model.Repo
import com.example.githubusers.core.model.User
import com.example.githubusers.core.model.UserDetail
import java.time.Instant
import java.time.LocalDate

/** テストとプレビューで使うデータ。 */
object TestData {
    fun user(id: Long, login: String = "user$id") = User(
        id = id,
        login = login,
        avatarUrl = "https://avatars.githubusercontent.com/u/$id?v=4",
        type = AccountType.User,
    )

    fun users(range: LongRange): List<User> = range.map { user(it) }

    fun userDetail(login: String = "mojombo") = UserDetail(
        id = 1,
        login = login,
        avatarUrl = "https://avatars.githubusercontent.com/u/1?v=4",
        htmlUrl = "https://github.com/$login",
        type = AccountType.User,
        name = "Tom Preston-Werner",
        company = null,
        blog = "http://tom.preston-werner.com",
        location = "San Francisco",
        email = null,
        bio = null,
        twitterUsername = "mojombo",
        publicRepos = 66,
        publicGists = 62,
        followers = 24_100,
        following = 11,
        createdAt = Instant.parse("2007-10-20T05:24:19Z"),
        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    fun repo(id: Long, name: String = "repo$id", stars: Int = 0) = Repo(
        id = id,
        name = name,
        description = "Description of $name",
        htmlUrl = "https://github.com/mojombo/$name",
        language = "Ruby",
        stargazersCount = stars,
        forksCount = 0,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        isFork = false,
    )

    fun deviceCode(
        userCode: String = "WDJB-MJHT",
        expiresAt: Instant = Instant.parse("2100-01-01T00:00:00Z"),
    ) = DeviceCode(
        deviceCode = "device-code-$userCode",
        userCode = userCode,
        verificationUri = "https://github.com/login/device",
        expiresAt = expiresAt,
        intervalSeconds = 5,
    )

    /**
     * [end] で終わる過去1年間（53週）のカレンダー。日曜始まりで、最後の週は [end] の曜日で終わる。
     * 件数は [countOf] で決める（初期値は 0 件）。
     */
    fun contributionCalendar(
        end: LocalDate = LocalDate.parse("2026-10-28"),
        countOf: (LocalDate) -> Int = { 0 },
    ): ContributionCalendar {
        val lastSunday = end.minusDays((end.dayOfWeek.value % 7).toLong())
        val firstSunday = lastSunday.minusWeeks(52)
        val weeks = (0 until 53).map { w ->
            val sunday = firstSunday.plusWeeks(w.toLong())
            ContributionWeek(
                days = (0 until 7).map { sunday.plusDays(it.toLong()) }
                    .filter { !it.isAfter(end) }
                    .map { date ->
                        val count = countOf(date)
                        ContributionDay(
                            date = date,
                            weekday = date.dayOfWeek.value % 7,
                            count = count,
                            level = when {
                                count == 0 -> ContributionLevel.None
                                count < 3 -> ContributionLevel.FirstQuartile
                                count < 6 -> ContributionLevel.SecondQuartile
                                count < 10 -> ContributionLevel.ThirdQuartile
                                else -> ContributionLevel.FourthQuartile
                            },
                        )
                    },
            )
        }
        return ContributionCalendar(totalContributions = weeks.sumOf { w -> w.days.sumOf { it.count } }, weeks = weeks)
    }
}
