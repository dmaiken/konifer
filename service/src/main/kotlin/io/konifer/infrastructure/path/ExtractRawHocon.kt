package io.konifer.infrastructure.path

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig

/**
 * Recursively unwraps Ktor's [ApplicationConfig] to extract and merge the underlying
 * Typesafe [Config] objects, maintaining the correct fallback priorities.
 */
fun ApplicationConfig.extractRawHocon(): Config {
    // Base Case 1: We hit the actual HOCON config layer
    if (this is HoconApplicationConfig) {
        return try {
            val field = HoconApplicationConfig::class.java.getDeclaredField("config")
            field.isAccessible = true
            field.get(this) as Config
        } catch (e: Exception) {
            println("Failed to extract raw Hocon config from HoconApplicationConfig: $e")
            ConfigFactory.empty()
        }
    }

    if (this.javaClass.simpleName == "MergedApplicationConfig") {
        return try {
            // Extract the primary config
            val configField = this.javaClass.getDeclaredField("first")
            configField.isAccessible = true
            val primaryKtorConfig = configField.get(this) as ApplicationConfig

            // Extract the fallback config
            val fallbackField = this.javaClass.getDeclaredField("second")
            fallbackField.isAccessible = true
            val fallbackKtorConfig = fallbackField.get(this) as ApplicationConfig

            // Recursively extract the raw HOCON from both
            val primaryRaw = primaryKtorConfig.extractRawHocon()
            val fallbackRaw = fallbackKtorConfig.extractRawHocon()

            // Merge them
            primaryRaw.withFallback(fallbackRaw)
        } catch (e: Exception) {
            println("Failed to extract raw Hocon config from MergedApplicationConfig: $e")
            ConfigFactory.empty()
        }
    }

    // Base Case 3: Other Ktor configs (MapApplicationConfig, etc.)
    return ConfigFactory.empty()
}
