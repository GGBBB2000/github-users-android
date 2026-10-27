package com.example.githubusers.core.testing

import com.example.githubusers.core.data.repository.ContributionRepository
import com.example.githubusers.core.model.ContributionCalendar
import com.example.githubusers.core.model.GitHubException

/**
 * テスト用の [ContributionRepository]。[calendar] を返す。
 *
 * [nextError] を設定すると、次の1回だけその例外を投げる。
 */
class FakeContributionRepository(
    var calendar: ContributionCalendar = TestData.contributionCalendar(),
) : ContributionRepository {
    var nextError: GitHubException? = null
    val requests = mutableListOf<String>()

    override suspend fun getContributionCalendar(login: String): ContributionCalendar {
        requests += login
        nextError?.let { nextError = null; throw it }
        return calendar
    }
}
