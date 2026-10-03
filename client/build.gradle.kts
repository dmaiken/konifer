import dev.detekt.gradle.Detekt
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotest)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
}

group = "io.konifer"
version = "0.0.1"

repositories {
    mavenCentral()
}

kotlin {
    withSourcesJar()

    jvm {
        testRuns.configureEach {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    sourceSets {
        commonMain {
            // Compile the shared models into the SDK, including their Kotlin metadata and serializers.
            // The service still uses :common, but SDK consumers do not need that unpublished artifact.
            kotlin.srcDir(rootProject.layout.projectDirectory.dir("common/src/commonMain/kotlin"))

            dependencies {
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
                api(libs.ktor.io)
                api(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.cryptography.core)
                implementation(libs.cryptography.provider.optimal)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.datetime)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            implementation(libs.logback.classic)
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/client.yml"))
}

tasks.named("detekt") {
    dependsOn("detektMainJvm")
}

tasks.named<Detekt>("detektMainJvm") {
    // The embedded shared models remain owned and linted by :common.
    setSource(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))
}

dokka {
    moduleName.set("konifer-client")
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("konifer-client")
}

tasks.named<Jar>("jvmSourcesJar") {
    archiveFileName.set("konifer-client-jvm-${project.version}-sources.jar")
}

val jvmJavadocJar =
    tasks.register<Jar>("jvmJavadocJar") {
        group = "build"
        description = "Assembles the client API documentation, including the shared models."
        archiveAppendix.set("jvm")
        archiveClassifier.set("javadoc")
        from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    }

tasks.named("assemble") {
    dependsOn("jvmSourcesJar", jvmJavadocJar)
}
