package io.konifer.asset

import io.konifer.BaseTestcontainerTest.Companion.BOUNDARY
import io.konifer.byteArrayToImage
import io.konifer.config.testInMemory
import io.konifer.domain.asset.AssetClass
import io.konifer.domain.asset.AssetSource
import io.konifer.domain.image.ImageFormat
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.util.UnValidatedStoreAssetRequest
import io.konifer.util.createJsonClient
import io.konifer.util.fetchAssetContent
import io.konifer.util.fetchAssetMetadata
import io.konifer.util.storeAssetMultipartSource
import io.konifer.util.storeAssetUrlSource
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
            paths = [
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
            paths = [
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
            paths = [
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

            fetchAssetContent(client, path = "users/123/profile", expectedMimeType = "image/png").second!!.let { imageBytes ->
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
            paths = [
              {
                path = "/**"
                object-store {
                  bucket = default-bucket
                }
              }
              {
                path = "/users/*/profile"
                object-store {
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
                    it.storeBucket shouldBe "correct-bucket"
                }
            }
        }

    @ParameterizedTest
    @EnumSource(ImageFormat::class)
    fun `can preprocess image to any every supported type`(format: ImageFormat) =
        testInMemory(
            """
            paths = [
              {
                path = "/**"
                preprocessing {
                  enabled = true
                  image {
                    format = ${format.format}
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
            ).second!!.let { imageBytes ->
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
            storeAssetResponse.variants.first().storeBucket shouldBe "assets"
            storeAssetResponse.variants.first().storeKey shouldNotBe null
            storeAssetResponse.variants
                .first()
                .attributes.format shouldBe "jpg"
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
                UnValidatedStoreAssetRequest(
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
                UnValidatedStoreAssetRequest(
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
                UnValidatedStoreAssetRequest(
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
                UnValidatedStoreAssetRequest(
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
                UnValidatedStoreAssetRequest(
                    labels = labels,
                )
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetMetadata(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }
}
