package io.direkt.asset

import io.byteArrayToImage
import io.direkt.BaseTestcontainerTest.Companion.BOUNDARY
import io.direkt.asset.handler.AssetSource
import io.direkt.asset.model.AssetClass
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.config.testInMemory
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetContent
import io.direkt.util.fetchAssetMetadata
import io.direkt.util.storeAssetMultipartSource
import io.direkt.util.storeAssetUrlSource
import io.image.model.ImageFormat
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class StoreAssetTest {
    @Test
    fun `uploading something not an image will return bad request`() =
        testInMemory {
            val client = createJsonClient()
            val image = "I am not an image".toByteArray()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            client
                .post("/assets") {
                    contentType(ContentType.MultiPart.FormData)
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "metadata",
                                    Json.encodeToString<StoreAssetRequest>(request),
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "application/json")
                                    },
                                )
                                append(
                                    "file",
                                    image,
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "image/png")
                                        append(HttpHeaders.ContentDisposition, "filename=\"ktor_logo.png\"")
                                    },
                                )
                            },
                            BOUNDARY,
                            ContentType.MultiPart.FormData.withParameter("boundary", BOUNDARY),
                        ),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                }
        }

    @Test
    fun `cannot store asset that is a disallowed content type`() =
        testInMemory(
            """
            path-configuration = [
              {
                path = "/users/*/profile"
                allowed-content-types = [
                  "image/jpeg"
                ]
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
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.Forbidden)
        }

    @Test
    fun `cannot store asset if no content type is allowed`() =
        testInMemory(
            """
            path-configuration = [
              {
                path = "/users/*/profile"
                allowed-content-types = [ ]
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
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.Forbidden)
        }

    @Test
    fun `can store asset if allowed-content-types is not defined for path`() =
        testInMemory(
            """
            path-configuration = [
              {
                path = "/users/*/profile"
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
            storeAssetMultipartSource(client, image, request, path = "users/123/profile")

            fetchAssetContent(client, path = "users/123/profile", expectedMimeType = "image/png")!!.let { imageBytes ->
                val rendered = byteArrayToImage(imageBytes)
                rendered.width shouldBe bufferedImage.width
                rendered.height shouldBe bufferedImage.height
                Tika().detect(imageBytes) shouldBe "image/png"
            }
        }

    @Test
    fun `object is stored in configured bucket`() =
        testInMemory(
            """
            path-configuration = [
              {
                path = "/**"
                s3 {
                  bucket = default-bucket
                }
              }
              {
                path = "/users/*/profile"
                s3 {
                  bucket = correct-bucket
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
            storeAssetMultipartSource(client, image, request, path = "users/123/profile")

            fetchAssetMetadata(client, path = "users/123/profile")!!.let { metadata ->
                metadata.variants.forAll {
                    it.bucket shouldBe "correct-bucket"
                }
            }
        }

    @ParameterizedTest
    @EnumSource(ImageFormat::class)
    fun `can convert image to any every supported type`(format: ImageFormat) =
        testInMemory(
            """
            path-configuration = [
              {
                path = "/**"
                image {
                  preprocessing {
                    image-format = ${format.format.first()}
                  }
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
            storeAssetMultipartSource(client, image, request, path = "users/123/profile")

            fetchAssetContent(
                client,
                path = "users/123/profile",
                expectedMimeType = format.mimeType,
            )!!.let { imageBytes ->
                Tika().detect(imageBytes) shouldBe format.mimeType
            }
        }

    @Test
    fun `can store asset uploaded as link`() =
        testInMemory(
            """
            source = {
              url = {
                allowed-domains = [
                  daniel.haxx.se
                ]
              }
            }
            """.trimIndent(),
        ) {
            val client = createJsonClient()
            // Come up with a better way to not rely on the internet
            val url = "https://daniel.haxx.se/daniel/b-daniel-at-snow.jpg"
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    url = url,
                )
            val storeAssetResponse = storeAssetUrlSource(client, request)
            storeAssetResponse!!.createdAt shouldNotBe null
            storeAssetResponse.variants.first().bucket shouldBe "assets"
            storeAssetResponse.variants.first().storeKey shouldNotBe null
            storeAssetResponse.variants
                .first()
                .attributes.mimeType shouldBe "image/jpeg"
            storeAssetResponse.`class` shouldBe AssetClass.IMAGE
            storeAssetResponse.alt shouldBe "an image"
            storeAssetResponse.entryId shouldBe 0
            storeAssetResponse.source shouldBe AssetSource.URL
            storeAssetResponse.sourceUrl shouldBe url
            fetchAssetMetadata(client, path = "profile") shouldBe storeAssetResponse
        }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "httpssss://daniel.haxx.se/daniel/b-daniel-at-snow.jpg",
            "url",
            "ftp://hello",
            "; DROP TABLE ASSET_TREE",
        ],
    )
    fun `cannot store asset uploaded as link if url is not valid`(badUrl: String) =
        testInMemory(
            """
            source = {
              url = {
                allowed-domains = [
                  daniel.haxx.se
                ]
              }
            }
            """.trimIndent(),
        ) {
            val client = createJsonClient()
            // Come up with a better way to not rely on the internet
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    url = badUrl,
                )
            storeAssetUrlSource(client, request, expectedStatus = HttpStatusCode.BadRequest)
            fetchAssetMetadata(client, path = "profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset using url if domain is not allowed`() =
        testInMemory(
            """
            source = {
              url = {
                allowed-domains = [ ]
              }
            }
            """.trimIndent(),
        ) {
            val client = createJsonClient()
            // Come up with a better way to not rely on the internet
            val url = "https://daniel.haxx.se/daniel/b-daniel-at-snow.jpg"
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    url = url,
                )

            storeAssetUrlSource(client, request, expectedStatus = HttpStatusCode.BadRequest)
            fetchAssetMetadata(client, path = "profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset via url that is larger than configured max value`() =
        testInMemory(
            """
            source = {
              url = {
                allowed-domains = [ daniel.haxx.se ]
                max-bytes = 100
              }
            }
            """.trimIndent(),
        ) {
            val client = createJsonClient()
            // Come up with a better way to not rely on the internet
            val url = "https://daniel.haxx.se/daniel/b-daniel-at-snow.jpg"
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    url = url,
                )

            storeAssetUrlSource(client, request, expectedStatus = HttpStatusCode.BadRequest)
            fetchAssetMetadata(client, path = "profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset via upload that is larger than configured max value`() =
        testInMemory(
            """
            source = {
              multipart = {
                max-bytes = 100
              }
            }
            """.trimIndent(),
        ) {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetMetadata(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with no upload or url source`() =
        testInMemory {
            val client = createJsonClient()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetUrlSource(client, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetMetadata(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with alt exceeding length limit`() =
        testInMemory {
            val client = createJsonClient()
            val request =
                StoreAssetRequest(
                    alt = "a".repeat(126),
                )
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetMetadata(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with tags exceeding length limit`() =
        testInMemory {
            val client = createJsonClient()
            val request =
                StoreAssetRequest(
                    tags = setOf("tag1", "a".repeat(257)),
                )
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetMetadata(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with label key exceeding length limit`() =
        testInMemory {
            val client = createJsonClient()
            val request =
                StoreAssetRequest(
                    labels =
                        mapOf(
                            "a" to "b",
                            "a".repeat(129) to "c",
                        ),
                )
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetMetadata(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with label value exceeding length limit`() =
        testInMemory {
            val client = createJsonClient()
            val request =
                StoreAssetRequest(
                    labels =
                        mapOf(
                            "a" to "b",
                            "d" to "c".repeat(257),
                        ),
                )
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetMetadata(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with too many labels`() =
        testInMemory {
            val client = createJsonClient()
            val labels =
                buildMap {
                    repeat(51) { idx ->
                        put(idx.toString(), idx.toString())
                    }
                }
            val request =
                StoreAssetRequest(
                    labels = labels,
                )
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetMetadata(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }
}
