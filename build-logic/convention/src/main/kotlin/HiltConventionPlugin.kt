import com.example.githubusers.buildlogic.libs
import com.example.githubusers.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        dependencies {
            add("ksp", libs.library("hilt-compiler"))
        }
        // Android モジュールでは Hilt の Gradle プラグインも使う
        listOf("com.android.application", "com.android.library").forEach { id ->
            pluginManager.withPlugin(id) {
                pluginManager.apply("com.google.dagger.hilt.android")
                dependencies {
                    add("implementation", libs.library("hilt-android"))
                }
            }
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            dependencies {
                add("implementation", libs.library("hilt-core"))
            }
        }
    }
}
