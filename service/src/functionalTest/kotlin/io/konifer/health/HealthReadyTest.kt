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

class HealthReadyTest : BaseFunctionalTest() {
    @Test
    fun `can perform readiness check successfully`() =
        testInMemory {
            client
                .get("health/ready") {
                    contentType(ContentType.Application.Json)
                }.let { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.body<ByteArray>() shouldHaveSize 0
                }
        }

    @Test
    fun `readiness check fails when a health indicator is unhealthy`() =
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
                .get("health/ready") {
                    contentType(ContentType.Application.Json)
                }.let { response ->
                    response.status shouldBe HttpStatusCode.ServiceUnavailable
                    response.body<ByteArray>() shouldHaveSize 0
                }
        }

    @Test
    fun `can perform readiness check without signature if signing is enabled`() =
        testInMemory(
            """
            url-signing {
              enabled = true
              secret-key = secret
            }
            """.trimIndent(),
        ) {
            client
                .get("health/ready") {
                    contentType(ContentType.Application.Json)
                }.let { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.body<ByteArray>() shouldHaveSize 0
                }
        }
}
