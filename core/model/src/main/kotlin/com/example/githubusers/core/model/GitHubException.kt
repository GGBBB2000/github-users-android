package com.example.githubusers.core.model

import java.time.Instant

/** データ層が投げるエラー。画面はこの種類を見て表示を切り替える。 */
sealed class GitHubException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** レート制限に達した。[resetAt] は制限が解除される時刻（わかる場合）。 */
    class RateLimited(val resetAt: Instant?, cause: Throwable? = null) :
        GitHubException("API rate limit exceeded (reset at $resetAt)", cause)

    /** ユーザーなどが見つからない（404）。 */
    class NotFound(cause: Throwable? = null) : GitHubException("Not found", cause)

    /** 通信できなかった（オフライン、タイムアウトなど）。 */
    class Network(cause: Throwable) : GitHubException("Network error", cause)

    /**
     * OAuth（Device Flow）のエラー。GitHub App の設定の誤り（`device_flow_disabled` など）で起きる。
     *
     * @param error GitHub が返した `error` の値
     */
    class OAuth(val error: String) : GitHubException("OAuth error: $error")

    /** その他の HTTP エラー。 */
    class Http(val code: Int, cause: Throwable? = null) : GitHubException("HTTP $code", cause)

    /** レスポンスが想定と違うなど、予期しないエラー。 */
    class Unexpected(cause: Throwable) : GitHubException("Unexpected error", cause)
}
