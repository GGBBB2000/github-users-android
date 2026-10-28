import java.util.Properties

plugins {
    alias(libs.plugins.githubusers.android.library)
    alias(libs.plugins.githubusers.hilt)
}

// 端末上の AI を、決まった文章を返すデモに差し替える（AICore のないエミュレーターでスクリーンショットを撮るため）。
// local.properties か -P で true にしたときの debug ビルドだけ。release ビルドでは常に使わない
val demoOnDeviceAiKey = "githubusers.demoOnDeviceAi"
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val demoOnDeviceAi: Boolean = (
    localProperties.getProperty(demoOnDeviceAiKey)
        ?: providers.gradleProperty(demoOnDeviceAiKey).orNull
    ).toBoolean()

android {
    namespace = "com.example.githubusers.core.ai"
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "DEMO_ON_DEVICE_AI", "$demoOnDeviceAi")
        }
        release {
            buildConfigField("boolean", "DEMO_ON_DEVICE_AI", "false")
        }
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.mlkit.genai.prompt)
}
