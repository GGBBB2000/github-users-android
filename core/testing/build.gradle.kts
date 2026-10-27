plugins {
    alias(libs.plugins.githubusers.android.library)
}

android {
    namespace = "com.example.githubusers.core.testing"
}

dependencies {
    api(project(":core:data"))
    api(project(":core:model"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
