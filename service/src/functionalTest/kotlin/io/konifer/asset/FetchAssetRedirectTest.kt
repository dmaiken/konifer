package io.konifer.asset

import io.konifer.byteArrayToImage
import io.konifer.config.testInMemory
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.infrastructure.http.APP_ALT
import io.konifer.util.createJsonClient
import io.konifer.util.fetchAssetViaRedirect
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
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
    fun `can fetch asset and render when redirect mode is bucket`() =
        testInMemory(
            """
            paths = [
              {
                path = "/**"
                object-store {
                  redirect-mode = bucket
                }
              }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val storedAssetInfo = storeAssetMultipartSource(client, image, request, path = "profile").second
            val variant = storedAssetInfo!!.variants.first()

            client.get("/assets/profile/-/redirect").apply {
                status shouldBe HttpStatusCode.TemporaryRedirect

                val location = Url(headers[HttpHeaders.Location]!!).toString()
                location shouldBe "http://localhost:8080/objectStore/${variant.storeBucket}/${variant.storeKey}"
            }
        }

    @Test
    fun `can fetch asset and render when redirect mode is cdn`() =
        testInMemory(
            """
            paths = [
              {
                path = "/**"
                object-store {
                  redirect-mode = cdn
                  cdn {
                    domain = "my.domain.com"
                  }
                }
              }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val storedAssetInfo = storeAssetMultipartSource(client, image, request, path = "profile").second

            client.get("/assets/profile/-/redirect").apply {
                status shouldBe HttpStatusCode.TemporaryRedirect
                headers[HttpHeaders.Location] shouldContain storedAssetInfo!!.variants.first().storeKey

                val variant = storedAssetInfo.variants.first()
                val location = Url(headers[HttpHeaders.Location]!!).toString()
                location shouldBe "https://my.domain.com/${variant.storeBucket}/${variant.storeKey}"
            }
        }

    @Test
    fun `returns content without redirect when redirect-mode is none`() =
        testInMemory(
            """
            paths = [
              {
                path = "/**"
                object-store {
                  redirect-mode = none
                }
              }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile").second

            client.get("/assets/profile/-/redirect").apply {
                status shouldBe HttpStatusCode.OK
                headers[HttpHeaders.Location] shouldBe null

                val body = bodyAsBytes()
                byteArrayToImage(body)
                Tika().detect(body) shouldBe "image/png"

                headers[APP_ALT] shouldBe request.alt
            }
        }
}
