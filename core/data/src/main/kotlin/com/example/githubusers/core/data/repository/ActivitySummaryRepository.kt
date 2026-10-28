package com.example.githubusers.core.data.repository

import com.example.githubusers.core.data.summary.ActivitySummaryInput
import com.example.githubusers.core.model.ActivitySummary
import com.example.githubusers.core.model.SummaryModelState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 端末上の AI による活動の要約。モデルの状態とダウンロード、作った要約をアプリ全体で管理する。
 *
 * 作った要約はプロセスが続く間、ログイン名ごとに覚えておく（同じユーザーを開き直したら、作り直さずに出す）。
 */
interface ActivitySummaryRepository {
    val modelState: StateFlow<SummaryModelState>

    /** モデルの状態を確かめ直す。ダウンロード中は何もしない。 */
    fun refreshModelState()

    /** モデルをダウンロードする。アプリ全体のスコープで動くので、画面を移っても続く。 */
    fun downloadModel()

    /** [login] について作った要約。まだなければ null。 */
    fun summary(login: String): Flow<ActivitySummary?>

    /**
     * 要約を作って覚えておく。失敗すると [com.example.githubusers.core.model.SummaryException] を投げる。
     */
    suspend fun summarize(input: ActivitySummaryInput): ActivitySummary
}
