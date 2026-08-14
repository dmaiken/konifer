package integration

import io.konifer.client.KoniferResponse
import io.konifer.common.http.StoreAssetRequest
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.UUID

class RedirectIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `can follow presigned redirects`() {
        runBlocking {
            val path = "presigned/${UUID.randomUUID()}"
            val (image, attributes) = ImageFactory.testImage()
            val storeResponse =
                client.storeAsset(
                    path = path,
                    format = attributes.format,
                    bytes = image,
                    request =
                        StoreAssetRequest(
                            alt = "image",
                            tags = setOf("tag1", "tag2"),
                            labels = mapOf("key1" to "value1", "key2" to "value2"),
                        ),
                )
            storeResponse::class shouldBe KoniferResponse.Success::class

            httpClient
                .prepareGet {
                    url.takeFrom("/assets/$path/-/redirect")
                }.execute { response ->
                    if (response.status.isSuccess()) {
                        response.headers["Content-Disposition"] shouldBe "inline"
                        response.headers["Content-Type"] shouldBe attributes.format.mimeType

                        response.bodyAsBytes() shouldBe image
                    } else {
                        fail("Request failed")
                    }
                }
        }
    }
}
