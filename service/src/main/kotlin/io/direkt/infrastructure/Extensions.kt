package io.direkt.infrastructure

import com.typesafe.config.ConfigException
import io.ktor.server.config.ApplicationConfig

fun ApplicationConfig.tryGetConfig(path: String): ApplicationConfig? =
    try {
        this.config(path)
    } catch (_: ConfigException) {
        null
    }
