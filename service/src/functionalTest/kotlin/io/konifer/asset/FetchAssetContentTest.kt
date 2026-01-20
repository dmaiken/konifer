package io.konifer.asset

import io.konifer.byteArrayToImage
import io.konifer.config.testInMemory
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.infrastructure.http.APP_ALT
import io.konifer.infrastructure.http.APP_LQIP_BLURHASH
import io.konifer.infrastructure.http.APP_LQIP_THUMBHASH
import io.konifer.util.createJsonClient
import io.konifer.util.fetchAssetContent
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpStatusCode
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import java.util.UUID

class FetchAssetContentTest {
    @Test
    fun `fetching asset content that does not exist returns not found`() =
        testInMemory {
            val client = createJsonClient()
            fetchAssetContent(client, path = UUID.randomUUID().toString(), expectedStatusCode = HttpStatusCode.NotFound)
        }

    @Test
    fun `can fetch asset and render`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    image {
                        lqip = [ "thumbhash", "blurhash" ]
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile")

            fetchAssetContent(client, path = "profile", expectedMimeType = "image/png").let { (response, imageBytes) ->
                val rendered = byteArrayToImage(imageBytes!!)
                rendered.width shouldBe bufferedImage.width
                rendered.height shouldBe bufferedImage.height
                Tika().detect(imageBytes) shouldBe "image/png"

                response.headers[APP_LQIP_BLURHASH] shouldNotBe null
                response.headers[APP_LQIP_THUMBHASH] shouldNotBe null
                response.headers[APP_ALT] shouldBe request.alt
            }
        }
}
