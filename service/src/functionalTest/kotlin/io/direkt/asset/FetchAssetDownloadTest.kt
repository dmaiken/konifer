package io.direkt.asset

import io.byteArrayToImage
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.config.testInMemory
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetContentDownload
import io.direkt.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import java.net.URLDecoder

class FetchAssetDownloadTest {
    @Test
    fun `can fetch asset and render with return format of download`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile")

            fetchAssetContentDownload(client, path = "profile", expectedMimeType = "image/png")!!.let { (contentDisposition, imageBytes) ->
                val rendered = byteArrayToImage(imageBytes)
                rendered.width shouldBe bufferedImage.width
                rendered.height shouldBe bufferedImage.height
                Tika().detect(imageBytes) shouldBe "image/png"
                contentDisposition shouldStartWith "attachment; filename*=UTF-8''"
                URLDecoder.decode(contentDisposition, Charsets.UTF_8) shouldContain "${request.alt!!}.png"
            }
        }

    @Test
    fun `path is used as filename if alt is not supplied`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request = StoreAssetRequest()
            storeAssetMultipartSource(client, image, request, path = "profile")

            fetchAssetContentDownload(client, path = "profile", expectedMimeType = "image/png")!!.let { (contentDisposition, imageBytes) ->
                val rendered = byteArrayToImage(imageBytes)
                rendered.width shouldBe bufferedImage.width
                rendered.height shouldBe bufferedImage.height
                Tika().detect(imageBytes) shouldBe "image/png"
                contentDisposition shouldStartWith "attachment; filename*=UTF-8''"
                URLDecoder.decode(contentDisposition, Charsets.UTF_8) shouldContain "profile.png"
            }
        }

    @Test
    fun `asset not found does not contain content disposition header`() =
        testInMemory {
            fetchAssetContentDownload(
                client,
                path = "profile/something-else",
                expectedStatusCode = HttpStatusCode.NotFound,
            ) shouldBe null
        }
}
