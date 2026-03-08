plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kotest)
}

group = "io.konifer"
version = "0.0.1"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js {
        outputModuleName = "konifer-client"
        generateTypeScriptDefinitions()
        browser()
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner)
        }

        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
