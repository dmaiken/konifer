package io.konifer.asset

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.BaseFunctionalTest
import io.konifer.testInMemory
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class FetchAssetTest : BaseFunctionalTest() {
    @Test
    fun `fetching an asset with an incorrect format returns bad request`() =
        testInMemory {
            client.get("/assets/${UuidCreator.getRandomBasedFast()}/-/invalid").apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
}
