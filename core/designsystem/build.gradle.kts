plugins {
    alias(libs.plugins.githubusers.android.compose)
}

android {
    namespace = "com.example.githubusers.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui)
}
