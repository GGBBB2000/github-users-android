package com.example.githubusers.core.network

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * サインインしていれば `Authorization: Bearer` を付ける。
 *
 * トークンは `api.github.com` に送るときだけ付ける（ほかのホストにトークンを渡さない）。
 */
internal class AuthInterceptor(
    private val tokenProvider: AuthTokenProvider,
    private val apiHost: String = API_HOST,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.shouldAuthorize(apiHost)) return chain.proceed(request)
        val token = tokenProvider.accessToken() ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "Bearer $token").build())
    }
}

/**
 * 401 が返ったときに呼ばれる。
 *
 * GitHub は無効なトークンを付けると公開 API でも 401 を返す。GitHub 側で許可が取り消されただけで
 * アプリが使えなくならないように、トークンを更新してやり直す。更新できなければサインアウトして、
 * トークンなしでやり直す。やり直しは1回まで。
 */
internal class TokenAuthenticator(
    private val tokenProvider: AuthTokenProvider,
    private val apiHost: String = API_HOST,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (!request.shouldAuthorize(apiHost) || response.priorResponse != null) return null
        val rejected = request.header(AUTHORIZATION)?.removePrefix("Bearer ") ?: return null
        val newToken = tokenProvider.onUnauthorized(rejected)
        return if (newToken != null) {
            request.newBuilder().header(AUTHORIZATION, "Bearer $newToken").build()
        } else {
            request.newBuilder().removeHeader(AUTHORIZATION).build()
        }
    }
}

private const val AUTHORIZATION = "Authorization"
private const val API_HOST = "api.github.com"

private fun Request.shouldAuthorize(apiHost: String): Boolean =
    url.host == apiHost && tag(ExplicitAuthorization::class.java) == null
