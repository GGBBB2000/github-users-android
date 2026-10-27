package com.example.githubusers.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * GitHub API の推奨ヘッダーを付ける。
 *
 * 認証を追加するときは、ここ（または別の Interceptor）で `Authorization` ヘッダーを付ける。
 */
internal class GitHubHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .build()
        return chain.proceed(request)
    }

    private companion object {
        const val API_VERSION = "2022-11-28"
    }
}
