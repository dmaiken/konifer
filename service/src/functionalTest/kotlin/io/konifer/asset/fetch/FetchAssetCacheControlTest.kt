package io.konifer.asset.fetch

import io.konifer.BaseFunctionalTest
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.selector.ReturnFormat
import io.konifer.testInMemory
import io.konifer.util.fetchAssetContent
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class FetchAssetCacheControlTest : BaseFunctionalTest() {
    @ParameterizedTest
    @EnumSource(ReturnFormat::class)
    fun `explicit cache headers apply to all GET return formats`(returnFormat: ReturnFormat) =
        testInMemory(
            configuration = """paths { "/**" { cache-control { enabled = true, max-age = 123 } } }""",
        ) {
            configureClient { followRedirects = false }
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            storeAssetMultipartSource(client, image, StoreAssetRequest())
            client.get("/assets/profile/-/${returnFormat.name.lowercase()}").apply {
                status shouldBe if (returnFormat == ReturnFormat.REDIRECT) HttpStatusCode.TemporaryRedirect else HttpStatusCode.OK
                headers[HttpHeaders.CacheControl] shouldBe "max-age=123"
                headers[HttpHeaders.Vary]!! shouldContain "Accept"
                bodyAsBytes()
            }
            client.get("/assets/missing/-/${returnFormat.name.lowercase()}").apply {
                status shouldBe if (returnFormat == ReturnFormat.ENTRIES) HttpStatusCode.OK else HttpStatusCode.NotFound
                headers[HttpHeaders.CacheControl] shouldBe "max-age=123"
                headers[HttpHeaders.Vary]!! shouldContain "Accept"
                bodyAsBytes()
            }
        }

    @Test
    fun `can fetch assets with cache-control header configured`() =
        testInMemory(
            """
            paths {
              "/**" {
                cache-control {
                  enabled = true
                  max-age = 50000
                  visibility = private
                  immutable = false
                }
              }
              "/users/456/*" {
                cache-control {
                  revalidate = no-cache
                }
              }
              "/users/123/profile" {
                cache-control {
                  enabled = true
                  max-age = 120000
                  s-maxage = 230000
                  visibility = public
                  revalidate = proxy-revalidate
                  stale-while-revalidate = 10000
                  stale-if-error = 300
                  immutable = true
                }
              }
              "/users/999/profile" {
                cache-control {
                  enabled = false
                }
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile")
            fetchAssetContent(client, path = "profile", expectedMimeType = "image/png").let { (response, _) ->
                response.headers[HttpHeaders.CacheControl] shouldBe "max-age=50000, private"
            }

            storeAssetMultipartSource(client, image, request, path = "users/456/profile")
            fetchAssetContent(client, path = "users/456/profile", expectedMimeType = "image/png").let { (response, _) ->
                response.headers[HttpHeaders.CacheControl] shouldBe "max-age=50000, private, no-cache"
            }

            storeAssetMultipartSource(client, image, request, path = "users/123/profile")
            fetchAssetContent(client, path = "users/123/profile", expectedMimeType = "image/png").let { (response, _) ->
                response.headers[HttpHeaders.CacheControl] shouldBe
                    "max-age=120000, s-maxage=230000, public, proxy-revalidate, stale-while-revalidate=10000, stale-if-error=300, immutable"
            }

            storeAssetMultipartSource(client, image, request, path = "users/999/profile")
            fetchAssetContent(client, path = "users/999/profile", expectedMimeType = "image/png").let { (response, _) ->
                response.headers[HttpHeaders.CacheControl] shouldBe null
            }
        }
}
