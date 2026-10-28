package com.example.githubusers.core.ai

import android.util.Log
import com.example.githubusers.core.model.SummaryException
import com.example.githubusers.core.model.SummaryModelState
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit の Prompt API（AICore の Gemini Nano）で文章を作る。
 *
 * クライアントは最初に使うときに作り、アプリのプロセスが続く間は使い回す（要約のたびに AICore につなぎ直さないため）。
 * 失敗したときはエラーコードをログに出す（対応端末での動作確認のため。内容は公開情報だけ）。
 */
@Singleton
internal class MlKitTextGenerationEngine @Inject constructor() : TextGenerationEngine {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    override suspend fun checkStatus(): SummaryModelState = try {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> SummaryModelState.Available
            FeatureStatus.DOWNLOADABLE -> SummaryModelState.Downloadable
            FeatureStatus.DOWNLOADING -> SummaryModelState.Downloading(downloadedBytes = 0, totalBytes = null)
            else -> SummaryModelState.Unavailable
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // AICore がない端末などでは、状態の確認そのものが失敗する
        Log.w(TAG, "checkStatus failed: ${e.errorCode()}", e)
        SummaryModelState.Unavailable
    }

    override fun download(): Flow<SummaryModelState.Downloading> = flow {
        var total: Long? = null
        try {
            model.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> {
                        total = status.bytesToDownload.takeIf { it > 0 }
                        emit(SummaryModelState.Downloading(downloadedBytes = 0, totalBytes = total))
                    }
                    is DownloadStatus.DownloadProgress ->
                        emit(SummaryModelState.Downloading(status.totalBytesDownloaded, total))
                    is DownloadStatus.DownloadFailed -> throw status.e
                    DownloadStatus.DownloadCompleted -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "download failed: ${e.errorCode()}", e)
            throw SummaryException.DownloadFailed(e)
        }
    }

    override suspend fun generate(prompt: String): GeneratedText {
        val request = generateContentRequest(TextPart(prompt)) {
            temperature = SummaryPrompt.TEMPERATURE
            topK = SummaryPrompt.TOP_K
            maxOutputTokens = SummaryPrompt.MAX_OUTPUT_TOKENS
            candidateCount = 1
        }
        val response = try {
            model.generateContent(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GenAiException) {
            Log.w(TAG, "generateContent failed: ${e.errorCode}", e)
            throw e.toSummaryException()
        } catch (e: Exception) {
            Log.w(TAG, "generateContent failed", e)
            throw SummaryException.Failed(e)
        }
        val candidate = response.candidates.firstOrNull()
            ?: throw SummaryException.InvalidOutput("no candidates")
        return GeneratedText(
            text = candidate.text,
            truncated = candidate.finishReason == Candidate.FinishReason.MAX_TOKENS,
        )
    }

    private fun GenAiException.toSummaryException(): SummaryException = when (errorCode) {
        GenAiException.ErrorCode.BUSY -> SummaryException.Busy(this)
        GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> SummaryException.QuotaExceeded(this)
        // アプリが前面でなくなった（AI は前面のアプリしか使えない）
        GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED,
        GenAiException.ErrorCode.CANCELLED,
        -> SummaryException.Interrupted(this)
        else -> SummaryException.Failed(this)
    }

    private fun Exception.errorCode(): Any? = (this as? GenAiException)?.errorCode

    private companion object {
        const val TAG = "OnDeviceAi"
    }
}
