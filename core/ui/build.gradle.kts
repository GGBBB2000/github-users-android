plugins {
    alias(libs.plugins.githubusers.android.compose)
}

android {
    namespace = "com.example.githubusers.core.ui"
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:model"))
    implementation(libs.androidx.browser)
    implementation(libs.coil.compose)
}
