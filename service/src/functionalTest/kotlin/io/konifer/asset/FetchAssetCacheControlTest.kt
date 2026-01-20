package io.konifer.asset

import io.konifer.config.testInMemory
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.util.createJsonClient
import io.konifer.util.fetchAssetContent
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import org.junit.jupiter.api.Test

class FetchAssetCacheControlTest {
    @Test
    fun `can fetch assets with cache-control header configured`() =
        testInMemory(
            """
                path-configuration = [
                {
                    path = "/**"
                    cache-control {
                        enabled = true
                        max-age = 50000
                        visibility = private
                        immutable = false
                    }
                }
                {
                    path = "/users/456/*"
                    cache-control {
                        revalidate = no-cache
                    }
                }
                {
                    path = "/users/123/profile"
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
                {
                    path = "/users/999/profile"
                    cache-control {
                        enabled = false
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient()
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
