package com.example.githubusers.core.data.repository

import com.example.githubusers.core.ai.ActivitySummarizer
import com.example.githubusers.core.data.di.ApplicationScope
import com.example.githubusers.core.data.summary.ActivitySummaryInput
import com.example.githubusers.core.data.summary.activityFacts
import com.example.githubusers.core.model.ActivitySummary
import com.example.githubusers.core.model.SummaryModelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultActivitySummaryRepository @Inject constructor(
    private val summarizer: ActivitySummarizer,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ActivitySummaryRepository {

    private val _modelState = MutableStateFlow<SummaryModelState>(SummaryModelState.Checking)
    override val modelState: StateFlow<SummaryModelState> = _modelState.asStateFlow()

    private val summaries = MutableStateFlow<Map<String, ActivitySummary>>(emptyMap())
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    override fun refreshModelState() {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        checkJob = scope.launch {
            when (val status = summarizer.checkStatus()) {
                // システムやほかのアプリがダウンロードしている。download() で進み具合と終わりを追う
                is SummaryModelState.Downloading -> downloadModel()
                else -> _modelState.value = status
            }
        }
    }

    override fun downloadModel() {
        if (downloadJob?.isActive == true) return
        _modelState.value = SummaryModelState.Downloading(downloadedBytes = 0, totalBytes = null)
        downloadJob = scope.launch {
            _modelState.value = try {
                summarizer.download().collect { _modelState.value = it }
                SummaryModelState.Available
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SummaryModelState.DownloadFailed
            }
        }
    }

    override fun summary(login: String): Flow<ActivitySummary?> =
        summaries.map { it[login] }.distinctUntilChanged()

    override suspend fun summarize(input: ActivitySummaryInput): ActivitySummary {
        val facts = activityFacts(input, LocalDate.now(clock))
        val text = summarizer.summarize(name = input.user.name ?: input.user.login, facts = facts)
        val summary = ActivitySummary(
            login = input.user.login,
            text = text,
            facts = facts,
            includesContributions = input.calendar != null,
        )
        summaries.update { it + (summary.login to summary) }
        return summary
    }
}
