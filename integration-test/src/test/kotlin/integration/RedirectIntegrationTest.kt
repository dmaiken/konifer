package integration

import io.konifer.clientV2.KoniferV2Result
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
                clientV2
                    .assets(path)
                    .newAsset()
                    .fromBytes(
                        bytes = image,
                        format = attributes.format,
                    ).withAlt("image")
                    .withLabels(mapOf("key1" to "value1", "key2" to "value2"))
                    .withTags(setOf("tag1", "tag2"))
                    .store()
            storeResponse::class shouldBe KoniferV2Result.Success::class

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
