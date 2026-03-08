plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "io.konifer"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js {
        generateTypeScriptDefinitions()
        browser()
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.datetime)
        }
    }
}
