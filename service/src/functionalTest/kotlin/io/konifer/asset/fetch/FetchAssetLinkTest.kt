package io.konifer.asset.fetch

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.byteArrayToImage
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.infrastructure.http.APP_CACHE_STATUS
import io.konifer.testInMemory
import io.konifer.util.fetchAssetLink
import io.konifer.util.storeAssetMultipartSource
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

class FetchAssetLinkTest : BaseFunctionalTest() {
    @Test
    fun `fetching asset that does not exist returns not found`() =
        testInMemory {
            fetchAssetLink(client, path = UuidCreator.getRandomBasedFast().toString(), expectedStatusCode = HttpStatusCode.NotFound)
        }

    @Test
    fun `can fetch asset and render`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
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
                    alt shouldBe request.alt
                    with(this.attributes) {
                        height shouldBe attributes.height
                        width shouldBe attributes.width
                        format shouldBe attributes.format.format
                        loop shouldBe null
                        pageCount shouldBe 1
                    }

                    url shouldBe "http://localhost/assets/profile/-/entry/${storedAssetInfo!!.entryId}/content"
                    val location =
                        shouldNotThrowAny {
                            Url(url).fullPath
                        }
                    val storeResponse = client.get(location)
                    storeResponse.status shouldBe HttpStatusCode.OK
                    val rendered = byteArrayToImage(storeResponse.bodyAsBytes())
                    rendered.width shouldBe attributes.width
                    rendered.height shouldBe attributes.height
                    Tika().detect(storeResponse.bodyAsBytes()) shouldBe attributes.format.mimeType
                }
            }
        }

    @Test
    fun `can fetch asset and render with lqip`() =
        testInMemory(
            """
            paths {
              "/**" {
                lqip = [ "thumbhash", "blurhash" ]
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val storedAssetInfo = storeAssetMultipartSource(client, image, request, path = "profile").second

            fetchAssetLink(client, path = "profile")!!.apply {
                lqip.blurhash shouldNotBe null
                lqip.thumbhash shouldNotBe null
                alt shouldBe request.alt

                url shouldBe "http://localhost/assets/profile/-/entry/${storedAssetInfo!!.entryId}/content"
                val location =
                    shouldNotThrowAny {
                        Url(url).fullPath
                    }
                val storeResponse = client.get(location)
                storeResponse.status shouldBe HttpStatusCode.OK
                val rendered = byteArrayToImage(storeResponse.bodyAsBytes())
                rendered.width shouldBe attributes.width
                rendered.height shouldBe attributes.height
                Tika().detect(storeResponse.bodyAsBytes()) shouldBe attributes.format.mimeType
            }
        }

    @Test
    fun `can fetch variant link and render`() =
        testInMemory(
            """
            paths {
              "/**" {
                lqip = [ "thumbhash", "blurhash" ]
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()
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
                    format = "png",
                    expectCacheHit = (count == 1),
                )!!.apply {
                    lqip.blurhash shouldNotBe null
                    lqip.thumbhash shouldNotBe null
                    alt shouldBe request.alt

                    url shouldNotContain storedAssetInfo!!.variants.first().storeKey
                    val location =
                        shouldNotThrowAny {
                            Url(url).fullPath
                        }
                    val storeResponse = client.get(location)
                    storeResponse.status shouldBe HttpStatusCode.OK
                    val rendered = byteArrayToImage(storeResponse.bodyAsBytes())
                    rendered.width shouldBe attributes.width
                    rendered.height shouldBe attributes.height
                    Tika().detect(storeResponse.bodyAsBytes()) shouldBe ImageFormat.PNG.mimeType
                }
                count++
            }
        }

    @Test
    fun `link preserves all query parameters from original request`() =
        testInMemory(
            """
            paths {
              "/**" {
                lqip = [ "thumbhash", "blurhash" ]
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()
            val labels =
                mapOf(
                    "phone" to "iphone",
                )
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    labels = labels,
                )
            val storedAssetInfo = storeAssetMultipartSource(client, image, request, path = "profile").second

            fetchAssetLink(
                client,
                path = "profile",
                format = "png",
                labels = labels,
            )!!.apply {
                lqip.blurhash shouldNotBe null
                lqip.thumbhash shouldNotBe null
                alt shouldBe request.alt

                url shouldNotContain storedAssetInfo!!.variants.first().storeKey
                val location =
                    shouldNotThrowAny {
                        Url(url).fullPath
                    }
                val storeResponse = client.get(location)
                storeResponse.status shouldBe HttpStatusCode.OK
                val rendered = byteArrayToImage(storeResponse.bodyAsBytes())
                rendered.width shouldBe attributes.width
                rendered.height shouldBe attributes.height
                Tika().detect(storeResponse.bodyAsBytes()) shouldBe ImageFormat.PNG.mimeType
            }
        }
}
