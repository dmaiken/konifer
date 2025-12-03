package io.direkt.asset

import io.byteArrayToImage
import io.direkt.APP_CACHE_STATUS
import io.direkt.asset.model.AssetLinkResponse
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.config.testInMemory
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetLink
import io.direkt.util.storeAssetMultipartSource
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.fullPath
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import java.util.UUID

class FetchAssetLinkTest {
    @Test
    fun `fetching asset that does not exist returns not found`() =
        testInMemory {
            val client = createJsonClient()
            fetchAssetLink(client, path = UUID.randomUUID().toString(), expectedStatusCode = HttpStatusCode.NotFound)
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

            client.get("/assets/profile/-/link").apply {
                status shouldBe HttpStatusCode.OK
                headers[HttpHeaders.Location] shouldBe null
                headers[APP_CACHE_STATUS] shouldBe "hit"
                body<AssetLinkResponse>().apply {
                    lqip.blurhash shouldBe null
                    lqip.thumbhash shouldBe null

                    url shouldContain "http://"
                    url shouldContain storedAssetInfo!!.variants.first().storeKey
                    url shouldEndWith ".png"
                    val location =
                        shouldNotThrowAny {
                            Url(url).fullPath
                        }
                    val storeResponse = client.get(location)
                    storeResponse.status shouldBe HttpStatusCode.OK
                    val rendered = byteArrayToImage(storeResponse.bodyAsBytes())
                    rendered.width shouldBe bufferedImage.width
                    rendered.height shouldBe bufferedImage.height
                    Tika().detect(storeResponse.bodyAsBytes()) shouldBe "image/png"
                }
            }
        }

    @Test
    fun `can fetch asset and render with lqip`() =
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
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val storedAssetInfo = storeAssetMultipartSource(client, image, request, path = "profile").second

            fetchAssetLink(client, path = "profile")!!.apply {
                lqip.blurhash shouldNotBe null
                lqip.thumbhash shouldNotBe null

                url shouldContain storedAssetInfo!!.variants.first().storeKey
                val location =
                    shouldNotThrowAny {
                        Url(url).fullPath
                    }
                val storeResponse = client.get(location)
                storeResponse.status shouldBe HttpStatusCode.OK
                val rendered = byteArrayToImage(storeResponse.bodyAsBytes())
                rendered.width shouldBe bufferedImage.width
                rendered.height shouldBe bufferedImage.height
                Tika().detect(storeResponse.bodyAsBytes()) shouldBe "image/png"
            }
        }

    @Test
    fun `can fetch variant link and render`() =
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
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val storedAssetInfo = storeAssetMultipartSource(client, image, request, path = "profile").second

            var count = 0
            repeat(2) {
                fetchAssetLink(
                    client,
                    path = "profile",
                    mimeType = "image/jpeg",
                    expectCacheHit = (count == 1),
                )!!.apply {
                    lqip.blurhash shouldNotBe null
                    lqip.thumbhash shouldNotBe null

                    url shouldNotContain storedAssetInfo!!.variants.first().storeKey
                    val location =
                        shouldNotThrowAny {
                            Url(url).fullPath
                        }
                    val storeResponse = client.get(location)
                    storeResponse.status shouldBe HttpStatusCode.OK
                    val rendered = byteArrayToImage(storeResponse.bodyAsBytes())
                    rendered.width shouldBe bufferedImage.width
                    rendered.height shouldBe bufferedImage.height
                    Tika().detect(storeResponse.bodyAsBytes()) shouldBe "image/jpeg"
                }
                count++
            }
        }
}
