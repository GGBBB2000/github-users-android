import com.example.githubusers.buildlogic.libs
import com.example.githubusers.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** 機能モジュール（画面と ViewModel）共通の設定。 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("githubusers.android.compose")
        pluginManager.apply("githubusers.hilt")
        // NavKey を保存・復元するため
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        dependencies {
            add("implementation", project(":core:data"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))

            add("implementation", libs.library("androidx-compose-material3"))
            add("implementation", libs.library("androidx-compose-material3-adaptive-layout"))
            add("implementation", libs.library("androidx-compose-material3-adaptive-navigation3"))
            add("implementation", libs.library("androidx-navigation3-runtime"))
            add("implementation", libs.library("kotlinx-serialization-json"))
            add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))

            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.library("turbine"))
        }
    }
}
