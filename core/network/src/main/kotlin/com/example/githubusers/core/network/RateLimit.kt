package com.example.githubusers.core.network

import okhttp3.Headers
import java.time.Instant

/**
 * レスポンスヘッダーから読み取ったレート制限の情報。
 *
 * @see <a href="https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api">Rate limits for the REST API</a>
 */
data class RateLimit(
    val remaining: Int?,
    val resetAt: Instant?,
) {
    val isExceeded: Boolean get() = remaining == 0

    companion object {
        fun from(headers: Headers): RateLimit = RateLimit(
            remaining = headers["x-ratelimit-remaining"]?.toIntOrNull(),
            resetAt = headers["x-ratelimit-reset"]?.toLongOrNull()?.let(Instant::ofEpochSecond),
        )
    }
}
