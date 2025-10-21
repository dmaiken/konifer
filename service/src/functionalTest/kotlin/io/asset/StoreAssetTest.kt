package io.asset

import io.BaseTestcontainerTest.Companion.BOUNDARY
import io.asset.model.AssetClass
import io.asset.model.StoreAssetRequest
import io.byteArrayToImage
import io.config.testInMemory
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
import io.util.createJsonClient
import io.util.fetchAssetContent
import io.util.fetchAssetInfo
import io.util.storeAssetMultipart
import io.util.storeAssetUrl
import kotlinx.serialization.json.Json
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class StoreAssetTest {
    @Test
    fun `uploading something not an image will return bad request`() =
        testInMemory {
            val client = createJsonClient()
            val image = "I am not an image".toByteArray()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            client.post("/assets") {
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
                    type = "image/png",
                    alt = "an image",
                )
            storeAssetMultipart(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.Forbidden)
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
                    type = "image/png",
                    alt = "an image",
                )
            storeAssetMultipart(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.Forbidden)
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
                    type = "image/png",
                    alt = "an image",
                )
            storeAssetMultipart(client, image, request, path = "users/123/profile")

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
                    type = "image/png",
                    alt = "an image",
                )
            storeAssetMultipart(client, image, request, path = "users/123/profile")

            fetchAssetInfo(client, path = "users/123/profile")!!.let { metadata ->
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
                    image-format = ${format.extension}
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
                    type = "image/png",
                    alt = "an image",
                )
            storeAssetMultipart(client, image, request, path = "users/123/profile")

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
        testInMemory {
            val client = createJsonClient()
            // Come up with a better way to not rely on the internet
            val url = "https://daniel.haxx.se/daniel/b-daniel-at-snow.jpg"
            val request =
                StoreAssetRequest(
                    type = "image/jpeg",
                    alt = "an image",
                    url = url,
                )
            val storeAssetResponse = storeAssetUrl(client, request)
            storeAssetResponse!!.createdAt shouldNotBe null
            storeAssetResponse.variants.first().bucket shouldBe "assets"
            storeAssetResponse.variants.first().storeKey shouldNotBe null
            storeAssetResponse.variants.first().imageAttributes.mimeType shouldBe "image/jpeg"
            storeAssetResponse.`class` shouldBe AssetClass.IMAGE
            storeAssetResponse.alt shouldBe "an image"
            storeAssetResponse.entryId shouldBe 0
            fetchAssetInfo(client, path = "profile") shouldBe storeAssetResponse
        }
}
