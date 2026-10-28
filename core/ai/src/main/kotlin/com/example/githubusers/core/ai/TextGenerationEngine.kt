package com.example.githubusers.core.ai

import com.example.githubusers.core.model.SummaryModelState
import kotlinx.coroutines.flow.Flow

/** 文章を作る仕組み（ML Kit の Prompt API、またはデモ）。 */
internal interface TextGenerationEngine {
    suspend fun checkStatus(): SummaryModelState

    fun download(): Flow<SummaryModelState.Downloading>

    suspend fun generate(prompt: String): GeneratedText
}

/** @param truncated 出力の上限に達して、途中で切れたか */
internal data class GeneratedText(val text: String, val truncated: Boolean)
