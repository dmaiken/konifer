import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.ExcludeDependenciesWithoutArtifactsFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.render.ReportRenderer
import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.license)
    alias(libs.plugins.kover)
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

licenseReport {
    renderers =
        arrayOf<ReportRenderer>(
            InventoryHtmlReportRenderer("report.html", "Backend"),
            JsonReportRenderer("report.json", true),
        )
    filters =
        arrayOf<DependencyFilter>(
            LicenseBundleNormalizer(),
            ExcludeDependenciesWithoutArtifactsFilter(),
        )
}

subprojects {
    pluginManager.withPlugin(kotlinId) {
        extensions.configure<KotlinBaseExtension> {
            jvmToolchain(25)
        }
    }

    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
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
dependencies {
    implementation(kotlin("stdlib-jdk8"))

    // Opt-in
    kover(project(":service"))
    kover(project(":client"))
    kover(project(":common"))
}

kover {
    reports {
        verify {
            rule("Maintain 85% Line Coverage") {
                // Total line coverage
                bound {
                    minValue = 85
                }
            }
        }
        filters {
            excludes {
                // This drops all jOOQ generated records, tables, and routines from the coverage calculation.
                classes(
                    "konifer.jooq.*",
                )
            }
        }
    }
}

tasks.koverXmlReport {
    // Enable for Codecov
    enabled = true
}

val koniferBaseImage =
    providers
        .gradleProperty("konifer.baseImage")
        .orElse("konifer-base:latest")

tasks.register<Exec>("buildKoniferDockerImage") {
    description = "Builds the Konifer Docker image used by integration tests."
    group = "build"

    dependsOn(":service:shadowJar")

    commandLine(
        "docker",
        "build",
        "--build-arg",
        "BASE_IMAGE=${koniferBaseImage.get()}",
        "-t",
        "ghcr.io/dmaiken/konifer:latest",
        rootDir.absolutePath,
    )
}

tasks.register("integrationTest") {
    description = "Runs Docker-based integration tests against a built Konifer image."
    group = "verification"

    dependsOn(":integration-test:integrationTest")
}

tasks.register("dockerIntegrationTest") {
    description = "Builds the Konifer Docker image locally and runs Docker-based integration tests."
    group = "verification"

    dependsOn("buildKoniferDockerImage")
    dependsOn("integrationTest")
}
