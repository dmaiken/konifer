import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import dev.detekt.gradle.Detekt
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotest)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

group = "io.konifer"
version = providers.gradleProperty("clientVersion").orElse("0.1.0").get()

repositories {
    mavenCentral()
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka(tasks.dokkaGeneratePublicationHtml),
        ),
    )
    coordinates(
        groupId = "io.konifer",
        artifactId = "konifer-client",
        version = project.version.toString(),
    )
    publishToMavenCentral()
    signAllPublications()

    pom {
        name = "Konifer Client"
        description = "Client for accessing a Konifer server"
        inceptionYear = "2026"
        url = "https://github.com/dmaiken/konifer/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "dmaiken"
                name = "Daniel Aiken"
                email = "daniel@konifer.io"
                url = "https://github.com/dmaiken/"
                organization = "Konifer"
                organizationUrl = "https://github.com/dmaiken/konifer/"
            }
        }
        scm {
            url = "https://github.com/dmaiken/konifer/"
            connection = "scm:git:https://github.com/dmaiken/konifer.git"
            developerConnection = "scm:git:ssh://git@github.com/dmaiken/konifer.git"
        }
        properties.put("kotlin.compiler.languageVersion", "2.3")
        properties.put("kotlin.compiler.apiVersion", "2.3")
    }
}

kotlin {
    jvmToolchain(17)
    withSourcesJar()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_3
        apiVersion = KotlinVersion.KOTLIN_2_3
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
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

tasks.named<Jar>("jvmJar") {
    from(projectDir) {
        include("LICENSE")
        into("META-INF")
    }
    archiveBaseName.set("konifer-client")
}

tasks.named<Jar>("jvmSourcesJar") {
    archiveFileName.set("konifer-client-jvm-${project.version}-sources.jar")
}

tasks.named<Jar>("sourcesJar") {
    // With only a JVM target, KMP's root sources JAR otherwise contains no common sources.
    from(kotlin.sourceSets.named("commonMain").map { it.kotlin }) {
        into("commonMain")
    }
}

tasks.named("assemble") {
    dependsOn("sourcesJar", "jvmSourcesJar", "jvmDokkaJavadocJar", "kotlinMultiplatformDokkaJavadocJar")
}
