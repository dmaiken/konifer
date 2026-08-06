package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.client.ContentFetchMode
import io.konifer.client.KoniferInternalTestApi
import io.konifer.common.asset.AssetClass
import io.konifer.common.asset.AssetSource
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.konifer.util.UnValidatedStoreAssetRequest
import io.konifer.util.fetchAssetContent
import io.konifer.util.fetchAssetInfo
import io.konifer.util.storeAssetMultipartSource
import io.konifer.util.storeAssetUrlSource
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@OptIn(KoniferInternalTestApi::class)
class StoreAssetTest : BaseFunctionalTest() {
    @Test
    fun `can store multipart asset when asset is sent before metadata`() =
        testInMemory {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request = StoreAssetRequest(alt = "asset-first upload")
            val boundary = "asset-first-boundary"

            val response =
                withTimeout(5_000) {
                    client.post("/assets/asset-first") {
                        contentType(ContentType.MultiPart.FormData)
                        setBody(
                            MultiPartFormDataContent(
                                formData {
                                    append(
                                        "asset",
                                        image,
                                        Headers.build {
                                            append(HttpHeaders.ContentType, ImageFormat.PNG.mimeType)
                                            append(HttpHeaders.ContentDisposition, "filename=\"asset-first.png\"")
                                        },
                                    )
                                    append(
                                        "metadata",
                                        Json.encodeToString(request),
                                        Headers.build {
                                            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                        },
                                    )
                                },
                                boundary,
                                ContentType.MultiPart.FormData.withParameter("boundary", boundary),
                            ),
                        )
                    }
                }

            response.status shouldBe HttpStatusCode.Created
            fetchAssetInfo(client, path = "asset-first")!!.alt shouldBe "asset-first upload"
        }

    @Test
    fun `uploading something not an image will return bad request`() =
        testInMemory {
            val image = "I am not an image".toByteArray()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )

            konifer().storeAsset(
                path = "",
                format = ImageFormat.PNG,
                request = request,
                bytes = image,
            ) shouldHaveHttpError HttpStatusCode.BadRequest.value
        }

    @Test
    fun `cannot store asset that is a disallowed content type`() =
        testInMemory(
            """
            paths {
              "/users/*/profile" {
                allowed-content-types = [
                  "image/jpeg"
                ]
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )

            konifer().storeAsset(
                path = "users/123/profile",
                format = ImageFormat.PNG,
                request = request,
                bytes = image,
            ) shouldHaveHttpError HttpStatusCode.Forbidden.value
        }

    @Test
    fun `cannot store asset if no content type is allowed`() =
        testInMemory(
            """
            paths {
              "/users/*/profile" {
                allowed-content-types = [ ]
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )

            konifer().storeAsset(
                path = "users/123/profile",
                format = ImageFormat.PNG,
                request = request,
                bytes = image,
            ) shouldHaveHttpError HttpStatusCode.Forbidden.value
        }

    @Test
    fun `can store asset if allowed-content-types is not defined for path`() =
        testInMemory(
            """
            paths {
              "/users/*/profile" {
              }
            }
            """.trimIndent(),
        ) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )

            konifer()
                .storeAsset(
                    path = "users/123/profile",
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = image,
                ).shouldBeSuccessful()

            val byteChannel = ByteChannel()
            val content =
                async {
                    byteChannel.toByteArray().also {
                        byteChannel.close()
                    }
                }
            konifer()
                .fetchAssetContent(
                    path = "users/123/profile",
                    byteChannel = byteChannel,
                    fetchMode = ContentFetchMode.CONTENT,
                ).shouldBeSuccessful()

            Tika().detect(content.await()) shouldBe ImageFormat.PNG.mimeType
        }

    @Test
    fun `object is stored in configured bucket`() =
        testInMemory(
            """
            paths {
              "/**" {
                object-store {
                  bucket = default-bucket
                }
              }
              "/users/*/profile" {
                object-store {
                  bucket = correct-bucket
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
            konifer()
                .storeAsset(
                    path = "users/123/profile",
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = image,
                ).shouldBeSuccessful()

            fetchAssetInfo(client, path = "users/123/profile")!!.let { metadata ->
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
            paths {
              "/**" {
                transform {
                  preprocessing {
                    enabled = true
                    image {
                      format = ${format.format}
                    }
                  }
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
            // Come up with a better way to not rely on the internet
            val url = "https://daniel.haxx.se/daniel/b-daniel-at-snow.jpg"
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    url = url,
                )
            val storeAssetResponse = storeAssetUrlSource(client, request)
            storeAssetResponse!!.variants.first().storeBucket shouldBe "assets"
            storeAssetResponse.variants
                .first()
                .attributes.format shouldBe "jpg"
            storeAssetResponse.`class` shouldBe AssetClass.IMAGE
            storeAssetResponse.alt shouldBe "an image"
            storeAssetResponse.entryId shouldBe 0
            storeAssetResponse.source shouldBe AssetSource.URL
            storeAssetResponse.sourceUrl shouldBe url
            fetchAssetInfo(client, path = "profile") shouldBe storeAssetResponse
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
            // Come up with a better way to not rely on the internet
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    url = badUrl,
                )
            storeAssetUrlSource(client, request, expectedStatus = HttpStatusCode.BadRequest)
            fetchAssetInfo(client, path = "profile", expectedStatus = HttpStatusCode.NotFound)
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
            // Come up with a better way to not rely on the internet
            val url = "https://daniel.haxx.se/daniel/b-daniel-at-snow.jpg"
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    url = url,
                )

            storeAssetUrlSource(client, request, expectedStatus = HttpStatusCode.BadRequest)
            fetchAssetInfo(client, path = "profile", expectedStatus = HttpStatusCode.NotFound)
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
            // Come up with a better way to not rely on the internet
            val url = "https://daniel.haxx.se/daniel/b-daniel-at-snow.jpg"
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    url = url,
                )

            storeAssetUrlSource(client, request, expectedStatus = HttpStatusCode.BadRequest)
            fetchAssetInfo(client, path = "profile", expectedStatus = HttpStatusCode.NotFound)
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
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetInfo(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with no upload or url source`() =
        testInMemory {
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetUrlSource(client, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetInfo(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with alt exceeding length limit`() =
        testInMemory {
            val request =
                UnValidatedStoreAssetRequest(
                    alt = "a".repeat(126),
                )
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetInfo(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with tags exceeding length limit`() =
        testInMemory {
            val request =
                UnValidatedStoreAssetRequest(
                    tags = setOf("tag1", "a".repeat(257)),
                )
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            storeAssetMultipartSource(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.BadRequest)

            fetchAssetInfo(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with label key exceeding length limit`() =
        testInMemory {
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

            fetchAssetInfo(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with label value exceeding length limit`() =
        testInMemory {
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

            fetchAssetInfo(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }

    @Test
    fun `cannot store asset with too many labels`() =
        testInMemory {
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

            fetchAssetInfo(client, path = "users/123/profile", expectedStatus = HttpStatusCode.NotFound)
        }
}
