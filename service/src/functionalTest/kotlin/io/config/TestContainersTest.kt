package io.config

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

fun testWithTestcontainers(
    postgres: PostgresTestContainerManager,
    localstack: LocalstackContainerManager,
    configuration: String? = null,
    testBody: suspend ApplicationTestBuilder.() -> Unit,
) {
    testApplication {
        environment {
            val testcontainersConfig =
                ConfigFactory.parseString(
                    """
                    postgres {
                        port = ${postgres.getPort()}
                    }
                    aws {
                        mock = true
                    }
                    localstack {
                        region = "${localstack.getRegion()}"
                        accessKey = "${localstack.getAccessKey()}"
                        secretKey = "${localstack.getSecretKey()}"
                        endpointUrl = "${localstack.getEndpointUrl()}"
                        port = ${localstack.getPort()}
                    }
                    """.trimIndent(),
                )
            config =
                HoconApplicationConfig(ConfigFactory.load())
                    .mergeWith(HoconApplicationConfig(testcontainersConfig))
                    .let { cfg ->
                        configuration?.let {
                            cfg.mergeWith(HoconApplicationConfig(ConfigFactory.parseString(it)))
                        } ?: cfg
                    }
        }
        testBody()
    }
}
