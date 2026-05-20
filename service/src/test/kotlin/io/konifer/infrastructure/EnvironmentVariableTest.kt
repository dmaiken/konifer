package io.konifer.infrastructure

import com.typesafe.config.ConfigFactory
import io.kotest.matchers.shouldBe
import io.ktor.server.config.HoconApplicationConfig
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable

class EnvironmentVariableTest {
    @Nested
    inner class TryGetStringWithEnvironmentVariableOverrideTests {
        @Test
        @SetEnvironmentVariable(key = "PG_USER", value = "test_admin")
        fun `environment variable overrides key`() {
            val config =
                """
                data-store {
                  postgresql {
                    user = "user"
                  }
                }
                """.trimIndent()

            val applicationConfig = HoconApplicationConfig(ConfigFactory.parseString(config))

            applicationConfig
                .tryGetConfig("data-store.postgresql")
                ?.tryGetStringWithEnvironmentVariableOverride(
                    key = "user",
                    environmentVariable = EnvironmentVariable.PG_USER,
                ) shouldBe "test_admin"
        }

        @Test
        fun `uses the keyed value if no environment variable exists`() {
            val config =
                """
                data-store {
                  postgresql {
                    user = "user"
                  }
                }
                """.trimIndent()

            val applicationConfig = HoconApplicationConfig(ConfigFactory.parseString(config))

            applicationConfig
                .tryGetConfig("data-store.postgresql")
                ?.tryGetStringWithEnvironmentVariableOverride(
                    key = "user",
                    environmentVariable = EnvironmentVariable.PG_USER,
                ) shouldBe "user"
        }
    }
}
