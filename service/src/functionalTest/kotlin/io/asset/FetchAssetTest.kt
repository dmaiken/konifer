package io.asset

import io.config.testInMemory
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.util.createJsonClient
import org.junit.jupiter.api.Test
import java.util.UUID

class FetchAssetTest {
    @Test
    fun `fetching an asset with an incorrect format returns bad request`() =
        testInMemory {
            val client = createJsonClient()
            client.get("/assets/${UUID.randomUUID()}/-/invalid").apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
}
