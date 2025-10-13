package io

import com.typesafe.config.ConfigException
import io.ktor.server.config.ApplicationConfig

fun ApplicationConfig.tryGetConfig(path: String): ApplicationConfig? {
    return try {
        this.config(path)
    } catch (_: ConfigException) {
        null
    }
}
