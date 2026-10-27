plugins {
    alias(libs.plugins.githubusers.android.library)
    alias(libs.plugins.githubusers.hilt)
}

android {
    namespace = "com.example.githubusers.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.kotlinx.serialization)
}
