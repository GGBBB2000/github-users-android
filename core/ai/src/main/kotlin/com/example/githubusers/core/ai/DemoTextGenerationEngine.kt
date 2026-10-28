package com.example.githubusers.core.ai

import com.example.githubusers.core.model.SummaryFactKind
import com.example.githubusers.core.model.SummaryModelState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/**
 * デモ用。AI を使わず、指示の「データ」から決まった形の文章を作る。
 *
 * AICore のないエミュレーターで、画面の確認とスクリーンショットの撮影に使う（`githubusers.demoOnDeviceAi=true` の debug ビルドだけ）。
 * 出力は本物と同じく [SummaryPrompt.validate] を通す。
 */
internal class DemoTextGenerationEngine @Inject constructor() : TextGenerationEngine {
    override suspend fun checkStatus(): SummaryModelState = SummaryModelState.Available

    override fun download(): Flow<SummaryModelState.Downloading> = emptyFlow()

    override suspend fun generate(prompt: String): GeneratedText {
        delay(GENERATION_DELAY_MILLIS)
        return GeneratedText(demoSummary(prompt), truncated = false)
    }

    private companion object {
        const val GENERATION_DELAY_MILLIS = 1_500L
    }
}

/** 指示の「データ」の行（`- ラベル: 値`）を読み取って、文章を組み立てる。 */
internal fun demoSummary(prompt: String): String {
    val values = prompt.substringAfter("## データ").lines()
        .filter { it.startsWith("- ") }
        .associate { it.removePrefix("- ").substringBefore(": ") to it.substringAfter(": ") }
    fun value(kind: SummaryFactKind) = values[SummaryPrompt.label(kind)]

    val sentences = mutableListOf<String>()
    val contributions = value(SummaryFactKind.Contributions)
    val weekday = value(SummaryFactKind.BusiestWeekday)
    if (contributions != null) {
        sentences += "過去1年間で ${contributions}のコントリビューションがあり" +
            (weekday?.let { "、特に${it}に活発です。" } ?: "ます。")
    }
    val languages = value(SummaryFactKind.TopLanguages)?.split("、")
    val repo = value(SummaryFactKind.TopRepo)?.substringBefore("（")
    when {
        languages != null && repo != null -> sentences += languages.usage("使い") + "、最も人気のあるリポジトリは $repo です。"
        languages != null -> sentences += languages.usage("使っています") + "。"
        repo != null -> sentences += "最も人気のあるリポジトリは $repo です。"
    }
    val month = value(SummaryFactKind.BusiestMonth)
    if (month != null) {
        sentences += "${month}が最も活動の多い月でした。"
    } else {
        val years = value(SummaryFactKind.YearsOnGitHub)
        val followers = value(SummaryFactKind.Followers)
        if (years != null && followers != null) sentences += "GitHub に登録して ${years}になり、$followers 人にフォローされています。"
    }
    return sentences.joinToString("")
}

/** 「Ruby を中心に JavaScript や C も使い」のように、言語の使い方を述べる。 */
private fun List<String>.usage(verb: String): String =
    if (size == 1) "${first()} を$verb" else "${first()} を中心に ${drop(1).joinToString(" や ")} も$verb"
