package io.konifer.asset.fetch

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.BaseFunctionalTest
import io.konifer.byteArrayToImage
import io.konifer.client.assets.fetch.requestedTransformation
import io.konifer.common.http.AssetContentHeaders
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.domain.image.ExifOrientations
import io.konifer.infrastructure.http.APP_ALT
import io.konifer.infrastructure.http.APP_LQIP_BLURHASH
import io.konifer.infrastructure.http.APP_LQIP_THUMBHASH
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.testInMemory
import io.konifer.util.fetchAssetContent
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream

class FetchAssetContentTest : BaseFunctionalTest() {
    @Test
    fun `fetching asset content that does not exist returns not found`() =
        testInMemory {
            fetchAssetContent(client, path = UuidCreator.getRandomBasedFast().toString(), expectedStatusCode = HttpStatusCode.NotFound)
        }

    @Test
    fun `can fetch asset and render`() =
        testInMemory(
            """
            paths {
              "/**" {
                lqip = [ "thumbhash", "blurhash" ]
              }
            }
            """.trimIndent(),
        ) {
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
                response.headers[AssetContentHeaders.WIDTH] shouldBe bufferedImage.width.toString()
                response.headers[AssetContentHeaders.HEIGHT] shouldBe bufferedImage.height.toString()
                response.headers[AssetContentHeaders.FORMAT] shouldBe "png"
                response.headers[AssetContentHeaders.ROTATE] shouldBe "0"
                response.headers[AssetContentHeaders.HORIZONTAL_FLIP] shouldBe "false"
                response.headers[AssetContentHeaders.PAGE_COUNT] shouldBe "1"
                response.headers[AssetContentHeaders.LOOP] shouldBe null
                response.headers[AssetContentHeaders.PIXELS_CHANNELS] shouldBe null
            }
        }

    @ParameterizedTest
    @ValueSource(strings = ["content", "download"])
    fun `content and download headers describe the selected transformed variant`(returnFormat: String) =
        testInMemory {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            konifer()
                .assets("headers")
                .newAsset()
                .fromBytes(image, ImageFormat.PNG)
                .store()
                .shouldBeSuccessful()
            repeat(2) {
                val response = client.get("/assets/headers/-/$returnFormat?w=40&format=pixels&pixel-channels=BGR&pixel-layout=planar")
                response.status shouldBe HttpStatusCode.OK
                response.headers[AssetContentHeaders.WIDTH] shouldBe "40"
                response.headers[AssetContentHeaders.FORMAT] shouldBe "pixels"
                response.headers[AssetContentHeaders.PIXELS_CHANNELS] shouldBe "bgr"
                response.headers[AssetContentHeaders.PIXELS_LAYOUT] shouldBe "planar"
                response.headers[AssetContentHeaders.PIXELS_DATA_TYPE] shouldBe "uint8"
                response.headers[AssetContentHeaders.ROTATE] shouldBe Rotate.ZERO.queryParameterValue
                response.headers[AssetContentHeaders.HORIZONTAL_FLIP] shouldBe "false"
            }
        }

    @Test
    fun `encoded output must be in the allowed formats`() =
        testInMemory(
            """
            paths {
              "/**" { allowed-formats = [png] }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            konifer()
                .assets("restricted")
                .newAsset()
                .fromBytes(image, ImageFormat.PNG)
                .store()
                .shouldBeSuccessful()

            client.get("/assets/restricted/-/content?w=40&format=jpg").status shouldBe HttpStatusCode.Forbidden
            client.get("/assets/restricted/-/content?w=40&format=png").status shouldBe HttpStatusCode.OK
        }

    @ParameterizedTest
    @ValueSource(ints = [2, 6])
    fun `original content exposes remaining orientation and auto rotated pixels expose normalized orientation`(orientation: Int) =
        testInMemory {
            val source = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val image = ByteArrayOutputStream()
            Vips.run { arena ->
                VImage.newFromBytes(arena, source).set("orientation", orientation).writeToStream(image, ".jpeg")
            }
            val asset = konifer().assets("oriented")
            asset
                .newAsset()
                .fromBytes(image.toByteArray(), ImageFormat.JPEG)
                .store()
                .shouldBeSuccessful()
            val original =
                asset
                    .originalVariant()
                    .fetchContent()
                    .shouldBeSuccessful()
                    .body
            val originalAttributes = checkNotNull(original.attributes)
            val (rotate, horizontalFlip) = ExifOrientations.fromExifOrientation(orientation)
            originalAttributes.rotate shouldBe rotate
            originalAttributes.horizontalFlip shouldBe horizontalFlip

            val variant =
                asset.variant(
                    requestedTransformation {
                        this.rotate = Rotate.AUTO
                        format = ImageFormat.PIXELS
                    },
                )
            val content = variant.fetchContent().shouldBeSuccessful().body
            val attributes = checkNotNull(content.attributes)
            attributes.rotate shouldBe Rotate.ZERO
            attributes.horizontalFlip shouldBe false
            attributes shouldBe
                variant
                    .fetchLink()
                    .shouldBeSuccessful()
                    .body.attributes
            if (orientation == 6) {
                attributes.width shouldBe originalAttributes.height
                attributes.height shouldBe originalAttributes.width
            }
        }
}
