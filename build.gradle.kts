import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "io.konifer"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

val detektId: String =
    libs.plugins.detekt
        .get()
        .pluginId
val kotlinId: String =
    libs.plugins.kotlin.jvm
        .get()
        .pluginId

subprojects {
    pluginManager.withPlugin(kotlinId) {
        extensions.configure<KotlinBaseExtension> {
            jvmToolchain(24)
        }
    }

    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(24))
            }
        }
    }

    if (name != "jooq-generated") {
        apply(plugin = detektId)
        detekt {
            config.setFrom("$rootDir/detekt.yml")
        }
        // Disable Detekt on test code
        tasks.withType<Detekt>().configureEach {
            exclude("**/test/**", "**/*Test.kt")
        }
    }
}
