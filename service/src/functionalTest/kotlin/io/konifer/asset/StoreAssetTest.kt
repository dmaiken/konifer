package io.konifer.asset

import io.konifer.client.KoniferInternalTestApi
import io.konifer.client.KoniferResponse
import io.konifer.common.asset.AssetClass
import io.konifer.common.asset.AssetSource
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.config.testInMemory
import io.konifer.util.UnValidatedStoreAssetRequest
import io.konifer.util.fetchAssetContent
import io.konifer.util.fetchAssetMetadata
import io.konifer.util.storeAssetMultipartSource
import io.konifer.util.storeAssetUrlSource
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.async
import org.apache.tika.Tika
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@OptIn(KoniferInternalTestApi::class)
class StoreAssetTest {
    @Test
    fun `uploading something not an image will return bad request`() =
        testInMemory {
            val image = "I am not an image".toByteArray()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )

            val response =
                konifer.storeAsset(
                    path = "",
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = image,
                )
            response::class shouldBe KoniferResponse.HttpError::class
            (response as KoniferResponse.HttpError).httpStatusCode shouldBe HttpStatusCode.BadRequest
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
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )

            val response =
                konifer.storeAsset(
                    path = "users/123/profile",
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = image,
                )
            response::class shouldBe KoniferResponse.HttpError::class
            (response as KoniferResponse.HttpError).httpStatusCode shouldBe HttpStatusCode.Forbidden
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
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )

            val response =
                konifer.storeAsset(
                    path = "users/123/profile",
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = image,
                )
            response::class shouldBe KoniferResponse.HttpError::class
            (response as KoniferResponse.HttpError).httpStatusCode shouldBe HttpStatusCode.Forbidden
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
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )

            val response =
                konifer.storeAsset(
                    path = "users/123/profile",
                    format = ImageFormat.PNG,
                    request = request,
                    bytes = image,
                )
            response::class shouldBe KoniferResponse.Success::class

            val byteChannel = ByteChannel()
            val content =
                async {
                    byteChannel.toByteArray().also {
                        byteChannel.close()
                    }
                }
            konifer.getAssetContent(
                path = "users/123/profile",
                byteChannel = byteChannel,
                requestRedirect = false,
            )

            response::class shouldBe KoniferResponse.Success::class
            Tika().detect(content.await()) shouldBe ImageFormat.PNG.mimeType
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
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            konifer.storeAsset(
                path = "users/123/profile",
                format = ImageFormat.PNG,
                request = request,
                bytes = image,
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
