package com.example.githubusers.core.ai

import com.example.githubusers.core.model.SummaryFact
import com.example.githubusers.core.model.SummaryModelState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class DefaultActivitySummarizer @Inject constructor(
    private val engine: TextGenerationEngine,
) : ActivitySummarizer {
    override suspend fun checkStatus(): SummaryModelState = engine.checkStatus()

    override fun download(): Flow<SummaryModelState.Downloading> = engine.download()

    override suspend fun summarize(name: String, facts: List<SummaryFact>): String {
        val output = engine.generate(SummaryPrompt.build(name, facts))
        return SummaryPrompt.validate(output, name, facts)
    }
}
