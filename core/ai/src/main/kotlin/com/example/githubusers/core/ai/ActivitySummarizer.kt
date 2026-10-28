package com.example.githubusers.core.ai

import com.example.githubusers.core.model.SummaryException
import com.example.githubusers.core.model.SummaryFact
import com.example.githubusers.core.model.SummaryModelState
import kotlinx.coroutines.flow.Flow

/**
 * 端末上の AI（Gemini Nano）で、ユーザーの活動を短い文章にまとめる。
 *
 * 数値はアプリで計算して [SummaryFact] で渡し、AI には文章にすることだけを任せる。
 * 失敗すると [SummaryException] を投げる。
 */
interface ActivitySummarizer {
    /**
     * 端末のモデルの状態。[SummaryModelState.Unavailable]、[SummaryModelState.Downloadable]、
     * [SummaryModelState.Downloading]（進み具合は不明）、[SummaryModelState.Available] のどれかを返す。
     */
    suspend fun checkStatus(): SummaryModelState

    /**
     * モデルをダウンロードする。進み具合を流し、終わったら完了する。
     * 失敗すると [SummaryException.DownloadFailed] を投げる。
     */
    fun download(): Flow<SummaryModelState.Downloading>

    /**
     * [facts] だけを使って、2〜3文の日本語の文章を作る。
     *
     * 出力に [facts] にない数値が含まれているとき、日本語でないときは [SummaryException.InvalidOutput] を投げる。
     *
     * @param name ユーザーの名前（なければログイン名）
     */
    suspend fun summarize(name: String, facts: List<SummaryFact>): String
}
