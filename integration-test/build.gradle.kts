import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":client"))
    testImplementation(project(":common"))
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

    testImplementation(libs.libvips.ffm)
    testImplementation(libs.tika.core)

    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.ktor.client.okhttp)
}

tasks.test {
    useJUnitPlatform()

    description = "Runs Docker-based integration tests against a built Konifer image."
    group = "verification"

    mustRunAfter(rootProject.tasks.named("buildKoniferDockerImage"))

    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
        events =
            setOf(
                TestLogEvent.FAILED,
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED,
                TestLogEvent.STANDARD_ERROR,
            )
        showStackTraces = true
        showCauses = true
    }

    // Tell libvips where to find jemalloc
    environment("LD_PRELOAD", "libjemalloc.so.2")
}

tasks.register("integrationTest") {
    description = "Runs Docker-based integration tests against a built Konifer image."
    group = "verification"

    dependsOn(tasks.test)
}
