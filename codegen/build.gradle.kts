plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("CodeGenKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.r2dbc.migrate)
    implementation(libs.r2dbc.migrate.resource.reader)
    implementation(libs.r2dbc.postgresql)
    implementation(libs.logback.classic)
    implementation(libs.testcontainers.postgresql)
    implementation(libs.jooq.codegen)
    implementation(libs.jooq.codegen.meta.extensions)
    implementation(libs.postresql)

    implementation(project(":service"))
}

tasks.register("generateJooq") {
    dependsOn("run")
}

tasks.test {
    useJUnitPlatform()
}
