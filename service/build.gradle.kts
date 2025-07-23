plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ktlint)
}

group = "io"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":jooq-generated"))

    implementation(libs.jooq)
    implementation(libs.jooq.kotlin)
    implementation(libs.jooq.kotlin.coroutines)
    implementation(libs.jooq.postgres.extensions)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.status.pages)

    implementation(libs.r2dbc.migrate)
    implementation(libs.r2dbc.migrate.resource.reader)
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    implementation(libs.kotlinx.coroutines.reactive)
    testImplementation(libs.ktor.client.content.negotiation)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)

    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.localstack)
    testImplementation(libs.testcontainers.jupiter)

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)

    implementation(awssdk.services.s3)

    implementation(libs.libvips.ffm)
    implementation(libs.tika.core)

    implementation(libs.zero.allocation.hashing)

    implementation(libs.blurhash)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("kotest.extensions.autoscan.disable", "true")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

sourceSets {
    create("functionalTest") {
        java.srcDir("src/functionalTest/kotlin")
        resources.srcDir("src/functionalTest/resources")
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += output + compileClasspath
    }
}

configurations.named("functionalTestImplementation") {
    extendsFrom(configurations["testImplementation"])
}

configurations.named("functionalTestRuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
}

tasks.register<Test>("functionalTest") {
    description = "Runs functional tests."
    group = "verification"

    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath

    shouldRunAfter(tasks.test)
}

tasks.named<ProcessResources>("processFunctionalTestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("check") {
    dependsOn("functionalTest")
}
