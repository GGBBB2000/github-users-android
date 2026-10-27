package com.example.githubusers.core.network.di

import android.content.Context
import com.example.githubusers.core.network.AuthInterceptor
import com.example.githubusers.core.network.AuthTokenProvider
import com.example.githubusers.core.network.BuildConfig
import com.example.githubusers.core.network.GitHubApi
import com.example.githubusers.core.network.GitHubHeadersInterceptor
import com.example.githubusers.core.network.GitHubOAuthApi
import com.example.githubusers.core.network.GitHubOAuthConfig
import com.example.githubusers.core.network.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class BaseClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class OAuthClient

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    /** HTTP キャッシュの上限。JSON だけなので小さくてよい。 */
    private const val HTTP_CACHE_SIZE_BYTES = 10L * 1024 * 1024

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOAuthConfig(): GitHubOAuthConfig = GitHubOAuthConfig(clientId = BuildConfig.GITHUB_CLIENT_ID)

    /** サインアウトしたときに消せるように、ほかのモジュールにも渡す。 */
    @Provides
    @Singleton
    fun provideCache(@ApplicationContext context: Context): Cache =
        Cache(File(context.cacheDir, "http_cache"), HTTP_CACHE_SIZE_BYTES)

    /** API 用と OAuth 用のクライアントの元。接続プールを共有する。 */
    @Provides
    @Singleton
    @BaseClient
    fun provideBaseOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    // URL だけを出す。HEADERS 以上にするとトークンがログに出るので注意
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }
            .build()

    /**
     * GitHub API 用の OkHttpClient。
     *
     * GitHub はレスポンスに `ETag` と `Cache-Control: max-age=60` を付けて返す。
     * OkHttp の HTTP キャッシュを使うと、60 秒以内はキャッシュから返し、それ以降は条件付きリクエストを送る。
     * 304 が返った場合はレート制限の回数に数えられない。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @BaseClient base: OkHttpClient,
        cache: Cache,
        tokenProvider: AuthTokenProvider,
    ): OkHttpClient =
        base.newBuilder()
            .cache(cache)
            .addInterceptor(GitHubHeadersInterceptor())
            .addInterceptor(AuthInterceptor(tokenProvider))
            .authenticator(TokenAuthenticator(tokenProvider))
            .build()

    /**
     * OAuth 用の OkHttpClient。キャッシュも認証ヘッダーも付けない。
     *
     * トークンの更新は API の通信を止めたまま行うので、API 用とは別の Dispatcher にする。
     */
    @Provides
    @Singleton
    @OAuthClient
    fun provideOAuthOkHttpClient(@BaseClient base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .dispatcher(Dispatcher())
            .addInterceptor { chain ->
                // 指定しないと、レスポンスが JSON ではなくフォーム形式で返る
                chain.proceed(chain.request().newBuilder().header("Accept", "application/json").build())
            }
            .build()

    @Provides
    @Singleton
    fun provideGitHubApi(okHttpClient: OkHttpClient, json: Json): GitHubApi =
        Retrofit.Builder()
            .baseUrl(GitHubApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)

    @Provides
    @Singleton
    fun provideGitHubOAuthApi(@OAuthClient okHttpClient: OkHttpClient, json: Json): GitHubOAuthApi =
        Retrofit.Builder()
            .baseUrl(GitHubOAuthApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubOAuthApi::class.java)
}
