package com.example.githubusers.core.testing

import com.example.githubusers.core.data.repository.ActivitySummaryRepository
import com.example.githubusers.core.data.summary.ActivitySummaryInput
import com.example.githubusers.core.model.ActivitySummary
import com.example.githubusers.core.model.SummaryException
import com.example.githubusers.core.model.SummaryFact
import com.example.githubusers.core.model.SummaryFactKind
import com.example.githubusers.core.model.SummaryModelState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * テスト用の [ActivitySummaryRepository]。要約の文章は「要約 1」「要約 2」…と、作った回数で変わる。
 *
 * [nextError] を設定すると、次の1回だけその例外を投げる。
 * [gate] を設定すると、完了するまで [summarize] が待つ（作っている途中の状態を確かめるため）。
 */
class FakeActivitySummaryRepository(
    initialModelState: SummaryModelState = SummaryModelState.Available,
) : ActivitySummaryRepository {
    override val modelState = MutableStateFlow(initialModelState)
    private val summaries = MutableStateFlow<Map<String, ActivitySummary>>(emptyMap())

    var nextError: SummaryException? = null
    var gate: CompletableDeferred<Unit>? = null
    val inputs = mutableListOf<ActivitySummaryInput>()
    var refreshCount = 0
        private set
    var downloadCount = 0
        private set

    override fun refreshModelState() {
        refreshCount++
    }

    override fun downloadModel() {
        downloadCount++
        modelState.value = SummaryModelState.Downloading(downloadedBytes = 0, totalBytes = null)
    }

    override fun summary(login: String): Flow<ActivitySummary?> = summaries.map { it[login] }

    override suspend fun summarize(input: ActivitySummaryInput): ActivitySummary {
        inputs += input
        gate?.await()
        nextError?.let { nextError = null; throw it }
        val summary = ActivitySummary(
            login = input.user.login,
            text = "要約 ${inputs.size}",
            facts = listOf(SummaryFact(SummaryFactKind.Followers, "24.1k")),
            includesContributions = input.calendar != null,
        )
        summaries.update { it + (summary.login to summary) }
        return summary
    }
}
