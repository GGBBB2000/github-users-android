plugins {
    alias(libs.plugins.githubusers.android.library)
    alias(libs.plugins.githubusers.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.githubusers.core.datastore"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.tink)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tink.android)
}
