package com.example.githubusers.core.network.di

import android.content.Context
import com.example.githubusers.core.network.BuildConfig
import com.example.githubusers.core.network.GitHubApi
import com.example.githubusers.core.network.GitHubHeadersInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import javax.inject.Singleton

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

    /**
     * GitHub API 用の OkHttpClient。
     *
     * GitHub はレスポンスに `ETag` と `Cache-Control: max-age=60` を付けて返す。
     * OkHttp の HTTP キャッシュを使うと、60 秒以内はキャッシュから返し、それ以降は条件付きリクエストを送る。
     * 304 が返った場合はレート制限の回数に数えられない。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http_cache"), HTTP_CACHE_SIZE_BYTES))
            .addInterceptor(GitHubHeadersInterceptor())
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
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
}
