package io.direkt.asset

import io.direkt.byteArrayToImage
import io.direkt.config.testInMemory
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetViaRedirect
import io.direkt.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.fullPath
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import java.util.UUID

class FetchAssetRedirectTest {
    @Test
    fun `fetching asset that does not exist returns not found`() =
        testInMemory {
            val client = createJsonClient()
            fetchAssetViaRedirect(
                client,
                path = UUID.randomUUID().toString(),
                expectedStatusCode = HttpStatusCode.NotFound,
            )
        }

    @Test
    fun `can fetch asset and render`() =
        testInMemory {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val storedAssetInfo = storeAssetMultipartSource(client, image, request, path = "profile").second

            client.get("/assets/profile/-/redirect").apply {
                status shouldBe HttpStatusCode.TemporaryRedirect
                headers[HttpHeaders.Location] shouldContain "http://"
                headers[HttpHeaders.Location] shouldContain storedAssetInfo!!.variants.first().storeKey

                val location = Url(headers[HttpHeaders.Location]!!).fullPath
                val storeResponse = client.get(location)
                storeResponse.status shouldBe HttpStatusCode.OK
                val rendered = byteArrayToImage(storeResponse.bodyAsBytes())
                rendered.width shouldBe bufferedImage.width
                rendered.height shouldBe bufferedImage.height
                Tika().detect(storeResponse.bodyAsBytes()) shouldBe "image/png"
            }
        }
}
