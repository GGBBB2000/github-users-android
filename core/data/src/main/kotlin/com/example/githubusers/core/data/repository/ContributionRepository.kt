package com.example.githubusers.core.data.repository

import com.example.githubusers.core.model.ContributionCalendar

/**
 * コントリビューションのカレンダー（草）を取得する。GraphQL API を使うので、**サインインが必要**。
 *
 * 失敗すると [com.example.githubusers.core.model.GitHubException] を投げる。
 * Organization と Bot には草がないので `NotFound` になる。
 */
interface ContributionRepository {
    /** 過去1年間のカレンダー。 */
    suspend fun getContributionCalendar(login: String): ContributionCalendar
}
