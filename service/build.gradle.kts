plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ktlint)
    `java-test-fixtures`
}

version = "0.0.1"
group = "io.direkt"

application {
    mainClass = "io.ApplicationKt"

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

java {
    sourceSets {
        val functionalTest by creating {
            kotlin.srcDir("src/functionalTest/kotlin")
            resources.srcDir("src/functionalTest/resources")
        }

        getByName("testFixtures") {
            resources.srcDir("src/testFixtures/resources")
        }
    }
}

val functionalTestImplementation: Configuration by configurations

configurations {
    // Make functionalTest see main + test + testFixtures
    named("functionalTestImplementation") {
        extendsFrom(configurations["implementation"])
        extendsFrom(configurations["testImplementation"])
        extendsFrom(configurations["testFixturesImplementation"])
    }

    named("functionalTestRuntimeOnly") {
        extendsFrom(configurations["runtimeOnly"])
        extendsFrom(configurations["testRuntimeOnly"])
        extendsFrom(configurations["testFixturesRuntimeOnly"])
    }
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
    implementation(libs.ktor.client.java)
    implementation(libs.uuid.creator)

    implementation(libs.r2dbc.migrate)
    implementation(libs.r2dbc.migrate.resource.reader)
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    implementation(libs.kotlinx.coroutines.reactive)
    testImplementation(libs.ktor.client.content.negotiation)

    implementation(libs.db.scheduler)
    implementation(libs.postresql)
    implementation(libs.hikari)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.awaitility)
    testImplementation(libs.awaitility.kotlin)

    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.localstack)
    testImplementation(libs.testcontainers.jupiter)
    testImplementation(libs.junit.pioneer)

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)

    implementation(awssdk.services.s3)

    implementation(libs.libvips.ffm)
    implementation(libs.tika.core)

    implementation(libs.blurhash)

    // Dependencies needed by testFixtures
    testFixturesImplementation(libs.kotlin.test.junit)
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation(libs.kotest.runner)
    testFixturesImplementation(libs.kotest.assertions)
    testFixturesImplementation(libs.libvips.ffm)
    testFixturesImplementation(libs.commons.math3)

    "functionalTestImplementation"(testFixtures(project))
}

/**
 * Necessary for Java 25 support - hopefully this can go away when ktor BOM supports Java 25
 */
configurations.all {
    resolutionStrategy {
        val bytebuddyVersion =
            libs.versions.bytebuddy.version
                .get()

        force("net.bytebuddy:byte-buddy:$bytebuddyVersion")
        force("net.bytebuddy:byte-buddy-agent:$bytebuddyVersion")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("kotest.extensions.autoscan.disable", "true")
}

tasks.register<Test>("functionalTest") {
    description = "Runs functional tests"
    group = "verification"

    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.named<ProcessResources>("processFunctionalTestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<ProcessResources>("processTestFixturesResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("check") {
    dependsOn("functionalTest")
}

kotlin {
    jvmToolchain(25)
}

ktor {
    docker {
        localImageName.set("direkt")
        imageTag.set("0.0.1-preview")
    }
}
