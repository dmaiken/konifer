package io.direkt.asset

import io.direkt.byteArrayToImage
import io.direkt.config.testInMemory
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.infrastructure.http.APP_ALT
import io.direkt.infrastructure.http.APP_LQIP_BLURHASH
import io.direkt.infrastructure.http.APP_LQIP_THUMBHASH
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetContentDownload
import io.direkt.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import java.net.URLDecoder

class FetchAssetDownloadTest {
    @Test
    fun `can fetch asset and render with return format of download`() =
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

            fetchAssetContentDownload(client, path = "profile", expectedMimeType = "image/png").let { (response, imageBytes) ->
                val rendered = byteArrayToImage(imageBytes!!)
                rendered.width shouldBe bufferedImage.width
                rendered.height shouldBe bufferedImage.height
                Tika().detect(imageBytes) shouldBe "image/png"
                response.headers[HttpHeaders.ContentDisposition] shouldStartWith "attachment; filename*=UTF-8''"
                URLDecoder.decode(response.headers[HttpHeaders.ContentDisposition], Charsets.UTF_8) shouldContain "${request.alt!!}.png"

                response.headers[APP_LQIP_BLURHASH] shouldNotBe null
                response.headers[APP_LQIP_THUMBHASH] shouldNotBe null
                response.headers[APP_ALT] shouldBe request.alt

                response.headers[HttpHeaders.ETag] shouldNotBe null
            }
        }

    @Test
    fun `path is used as filename if alt is not supplied`() =
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
            val request = StoreAssetRequest()
            storeAssetMultipartSource(client, image, request, path = "profile")

            fetchAssetContentDownload(client, path = "profile", expectedMimeType = "image/png").let { (response, imageBytes) ->
                val rendered = byteArrayToImage(imageBytes!!)
                rendered.width shouldBe bufferedImage.width
                rendered.height shouldBe bufferedImage.height
                Tika().detect(imageBytes) shouldBe "image/png"
                response.headers[HttpHeaders.ContentDisposition] shouldStartWith "attachment; filename*=UTF-8''"
                URLDecoder.decode(response.headers[HttpHeaders.ContentDisposition], Charsets.UTF_8) shouldContain "profile.png"

                response.headers[APP_LQIP_BLURHASH] shouldNotBe null
                response.headers[APP_LQIP_THUMBHASH] shouldNotBe null
                response.headers[APP_ALT] shouldBe null
            }
        }

    @Test
    fun `asset not found does not contain content disposition header`() =
        testInMemory {
            fetchAssetContentDownload(
                client,
                path = "profile/something-else",
                expectedStatusCode = HttpStatusCode.NotFound,
            ).let { (response, imageBytes) ->
                imageBytes shouldBe null
                response.headers[HttpHeaders.ContentDisposition] shouldBe null

                response.headers[APP_LQIP_BLURHASH] shouldBe null
                response.headers[APP_LQIP_THUMBHASH] shouldBe null
                response.headers[APP_ALT] shouldBe null
            }
        }
}
