package io.konifer.health

import io.konifer.BaseFunctionalTest
import io.konifer.testInMemory
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Test

class HealthTest : BaseFunctionalTest() {
    @Test
    fun `can health check successfully`() =
        testInMemory {
            client
                .get("health") {
                    contentType(ContentType.Application.Json)
                }.let { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.body<Map<String, String>>()["status"] shouldBe "okay"
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
                .get("health") {
                    contentType(ContentType.Application.Json)
                }.let { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.body<Map<String, String>>()["status"] shouldBe "okay"
                }
        }
}
