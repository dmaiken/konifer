package io.konifer.health

import io.konifer.BaseFunctionalTest
import io.konifer.infrastructure.health.HealthIndicator
import io.konifer.testInMemory
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Test
import org.koin.dsl.module

class HealthLiveTest : BaseFunctionalTest() {
    @Test
    fun `can perform liveness check successfully`() =
        testInMemory {
            client
                .get("health/live") {
                    contentType(ContentType.Application.Json)
                }.let { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.body<ByteArray>() shouldHaveSize 0
                }
        }

    @Test
    fun `liveness check does not fail when a health indicator is unhealthy`() =
        testInMemory(
            modules =
                listOf(
                    module {
                        single<HealthIndicator> {
                            object : HealthIndicator {
                                override fun isHealthy(): Boolean = false
                            }
                        }
                    },
                ),
        ) {
            client
                .get("health/live") {
                    contentType(ContentType.Application.Json)
                }.let { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.body<ByteArray>() shouldHaveSize 0
                }
        }

    @Test
    fun `can health check without signature if signing is enabled`() =
        testInMemory(
            """
            url-signing {
              enabled = true
              secret-key = secret
            }
            """.trimIndent(),
        ) {
            client
                .get("health/live") {
                    contentType(ContentType.Application.Json)
                }.let { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.body<ByteArray>() shouldHaveSize 0
                }
        }
}
