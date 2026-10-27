package com.example.githubusers.core.data

import com.example.githubusers.core.network.RateLimit
import com.example.githubusers.core.model.GitHubException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException

/** API 呼び出しの例外を [GitHubException] に変換する。 */
internal suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    throw e.toGitHubException()
} catch (e: IOException) {
    throw GitHubException.Network(e)
} catch (e: SerializationException) {
    throw GitHubException.Unexpected(e)
} catch (e: DateTimeParseException) {
    throw GitHubException.Unexpected(e)
}

internal fun HttpException.toGitHubException(): GitHubException {
    val headers = response()?.headers()
    val rateLimit = headers?.let(RateLimit::from)
    // 一次制限は remaining=0、二次制限は retry-after で知らされる
    val retryAfter = headers?.get("retry-after")?.toLongOrNull()
    return when {
        (code() == 403 || code() == 429) && rateLimit?.isExceeded == true ->
            GitHubException.RateLimited(rateLimit.resetAt, this)

        (code() == 403 || code() == 429) && retryAfter != null ->
            GitHubException.RateLimited(Instant.now().plusSeconds(retryAfter), this)

        code() == 404 -> GitHubException.NotFound(this)
        else -> GitHubException.Http(code(), this)
    }
}
