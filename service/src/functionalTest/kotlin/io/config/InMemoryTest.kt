package io.config

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

fun testInMemory(
    configuration: String? = null,
    testBody: suspend ApplicationTestBuilder.() -> Unit,
) {
    testApplication {
        environment {
            val inMemoryConfig =
                ConfigFactory.parseString(
                    """
                    object-store {
                        in-memory = true
                    }
                    database {
                        in-memory = true
                    }
                    """.trimIndent(),
                )
            config =
                HoconApplicationConfig(ConfigFactory.load())
                    .mergeWith(HoconApplicationConfig(inMemoryConfig))
                    .let { cfg ->
                        configuration?.let {
                            cfg.mergeWith(HoconApplicationConfig(ConfigFactory.parseString(it)))
                        } ?: cfg
                    }
        }
        testBody()
    }
}
