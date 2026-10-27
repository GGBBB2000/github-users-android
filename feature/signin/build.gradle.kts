plugins {
    alias(libs.plugins.githubusers.android.feature)
}

android {
    namespace = "com.example.githubusers.feature.signin"
}

dependencies {
    // ダイアログで表示するための DialogSceneStrategy
    implementation(libs.androidx.navigation3.ui)
}
