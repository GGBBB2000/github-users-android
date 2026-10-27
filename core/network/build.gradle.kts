plugins {
    alias(libs.plugins.githubusers.android.library)
    alias(libs.plugins.githubusers.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.githubusers.core.network"
    buildFeatures {
        buildConfig = true
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
