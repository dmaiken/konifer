package io.konifer.infrastructure

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString

enum class EnvironmentVariable {
    IN_MEMORY,
    PG_USER,
    PG_PASSWORD,
    S3_SECRET_KEY,
    URL_SIGNING_SECRET_KEY,
}

fun ApplicationConfig.tryGetStringWithEnvironmentVariableOverride(
    key: String,
    environmentVariable: EnvironmentVariable,
) = System.getenv(environmentVariable.name) ?: this.tryGetString(key)
