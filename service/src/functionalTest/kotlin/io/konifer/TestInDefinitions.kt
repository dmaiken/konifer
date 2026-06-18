package io.konifer

import com.typesafe.config.ConfigFactory
import io.konifer.infrastructure.datastore.postgres.createR2dbcDslContext
import io.ktor.http.ContentType
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.coroutineScope
import org.koin.core.module.Module
import org.testcontainers.postgresql.PostgreSQLContainer

fun testInMemory(
    configuration: String? = null,
    modules: List<Module> = emptyList(),
    testBody: suspend KoniferTestScope.() -> Unit,
) {
    testKoniferApplication(
        configuration = configuration,
        modules = modules,
        baseConfiguration =
            """
            object-store {
                provider = in-memory
            }
            data-store {
                provider = in-memory
            }
            """.trimIndent(),
        testBody = testBody,
    )
}

fun testPostgres(
    postgres: PostgreSQLContainer,
    configuration: String? = null,
    modules: List<Module> = emptyList(),
    testBody: suspend KoniferTestScope.() -> Unit,
) {
    createR2dbcDslContext(postgres)
    testKoniferApplication(
        configuration = configuration,
        modules = modules,
        baseConfiguration =
            """
            object-store {
                provider = in-memory
            }
            data-store {
                provider = postgresql
                postgresql {
                    host = "${postgres.host}"
                    port = ${postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)}
                    database = "${postgres.databaseName}"
                    user = "${postgres.username}"
                    password = "${postgres.password}"
                    ssl-mode = disable
                }
            }
            """.trimIndent(),
        testBody = testBody,
    )
}

private fun testKoniferApplication(
    configuration: String? = null,
    modules: List<Module> = emptyList(),
    baseConfiguration: String,
    testBody: suspend KoniferTestScope.() -> Unit,
) {
    testApplication {
        routing {
            get("/test-image") {
                val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
                call.respondBytes(image, ContentType.Application.OctetStream)
            }
        }
        environment {
            val inMemoryConfig =
                ConfigFactory.parseString(
                    """
                    ktor {
                        application {
                            modules = []
                        }
                    }
                    $baseConfiguration
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
        application {
            serviceModule(additionalModules = modules)
        }
        coroutineScope {
            KoniferTestScope(this@testApplication, this).testBody()
        }
    }
}

private fun PostgreSQLContainer.installLtree() {
    execInContainer(
        "psql",
        "-U",
        username,
        "-d",
        databaseName,
        "-c",
        "CREATE EXTENSION IF NOT EXISTS ltree;",
    )
}
