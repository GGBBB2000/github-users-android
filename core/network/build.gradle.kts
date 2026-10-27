import java.util.Properties

plugins {
    alias(libs.plugins.githubusers.android.library)
    alias(libs.plugins.githubusers.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// GitHub App の Client ID。local.properties にあればそちらを優先する（自分の GitHub App で試すとき）
val oauthClientIdKey = "githubusers.oauthClientId"
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val oauthClientId: String = localProperties.getProperty(oauthClientIdKey)
    ?: providers.gradleProperty(oauthClientIdKey).orNull
    ?: ""

android {
    namespace = "com.example.githubusers.core.network"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$oauthClientId\"")
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
    api(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(libs.okhttp.mockwebserver)
}
