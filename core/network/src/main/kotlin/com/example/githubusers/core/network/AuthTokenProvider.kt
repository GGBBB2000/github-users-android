package com.example.githubusers.core.network

/**
 * API の呼び出しに付けるアクセストークンを渡す。実装は `:core:data` にある。
 *
 * OkHttp のスレッドから同期的に呼ばれる。
 */
interface AuthTokenProvider {
    /** 今のアクセストークン。サインインしていなければ null。期限が近ければ更新してから返す。 */
    fun accessToken(): String?

    /**
     * [rejectedToken] で 401 が返ったときに呼ぶ。
     *
     * @return やり直しに使う新しいトークン。更新できずにサインアウトしたときは null（トークンなしでやり直す）
     */
    fun onUnauthorized(rejectedToken: String): String?
}

/**
 * 呼び出し側が `Authorization` ヘッダーを自分で付けたリクエストに付けるタグ。
 * [AuthInterceptor] と [TokenAuthenticator] はこのリクエストに手を出さない（サインイン中の `GET /user` など）。
 */
object ExplicitAuthorization
