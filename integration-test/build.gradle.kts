plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":client"))
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.runner.junit5)

    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.jupiter)
    testImplementation(libs.postresql)

    testImplementation(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}
