import com.example.githubusers.buildlogic.configureKotlinJvm
import com.example.githubusers.buildlogic.libs
import com.example.githubusers.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlinJvm()
        dependencies {
            add("testImplementation", libs.library("junit"))
        }
    }
}
