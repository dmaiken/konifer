package asset

import BaseTestcontainerTest.Companion.BOUNDARY
import asset.model.StoreAssetRequest
import config.testInMemory
import image.model.ImageFormat
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
import kotlinx.serialization.json.Json
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import util.byteArrayToImage
import util.createJsonClient
import util.fetchAssetContent
import util.fetchAssetInfo
import util.storeAsset

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
            storeAsset(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.Forbidden)
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
            storeAsset(client, image, request, path = "users/123/profile", expectedStatus = HttpStatusCode.Forbidden)
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
            storeAsset(client, image, request, path = "users/123/profile")

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
            storeAsset(client, image, request, path = "users/123/profile")

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
            storeAsset(client, image, request, path = "users/123/profile")

            fetchAssetContent(client, path = "users/123/profile", expectedMimeType = format.mimeType)!!.let { imageBytes ->
                Tika().detect(imageBytes) shouldBe format.mimeType
            }
        }
}
