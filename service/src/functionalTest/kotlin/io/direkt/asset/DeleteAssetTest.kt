package io.direkt.asset

import io.direkt.config.testInMemory
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.service.context.modifiers.OrderBy
import io.direkt.util.assertAssetDoesNotExist
import io.direkt.util.createJsonClient
import io.direkt.util.deleteAsset
import io.direkt.util.deleteAssetsAtPath
import io.direkt.util.deleteAssetsRecursivelyAtPath
import io.direkt.util.fetchAssetMetadata
import io.direkt.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import java.util.UUID

class DeleteAssetTest {
    @Test
    fun `deleting asset that does not exist returns no content`() =
        testInMemory {
            val client = createJsonClient()
            client
                .delete("/assets/${UUID.randomUUID()}")
                .apply {
                    status shouldBe HttpStatusCode.NoContent
                    bodyAsText() shouldBe ""
                }
        }

    @Test
    fun `can delete asset by path`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile")
            fetchAssetMetadata(client, path = "profile")
            deleteAsset(client, path = "profile")
            assertAssetDoesNotExist(client, path = "profile")
            deleteAsset(client, path = "profile")
        }

    @Test
    fun `deleting asset by path causes next oldest asset to be returned when fetching by path`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val firstAsset = storeAssetMultipartSource(client, image, request, path = "profile").second
            val secondAsset = storeAssetMultipartSource(client, image, request, path = "profile").second

            fetchAssetMetadata(client, path = "profile")!!.apply {
                entryId shouldBe secondAsset?.entryId
            }
            deleteAsset(client, path = "profile")
            fetchAssetMetadata(client, path = "profile")!!.apply {
                entryId shouldBe firstAsset?.entryId
            }
        }

    @Test
    fun `can delete asset by path and entryId`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val firstAsset = storeAssetMultipartSource(client, image, request, path = "profile").second
            val secondAsset = storeAssetMultipartSource(client, image, request, path = "profile").second

            deleteAsset(client, path = "profile", entryId = firstAsset!!.entryId)

            fetchAssetMetadata(client, path = "profile")!!.apply {
                entryId shouldBe secondAsset?.entryId
            }
            fetchAssetMetadata(
                client,
                path = "profile",
                entryId = firstAsset.entryId,
                expectedStatus = HttpStatusCode.NotFound,
            )
        }

    @Test
    fun `can delete assets by path and order`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val firstAsset = storeAssetMultipartSource(client, image, request, path = "profile").second
            val secondAsset = storeAssetMultipartSource(client, image, request, path = "profile").second

            deleteAssetsAtPath(client, path = "profile", orderBy = OrderBy.CREATED, limit = 1)

            fetchAssetMetadata(client, path = "profile")!!.apply {
                entryId shouldBe firstAsset?.entryId
            }
            fetchAssetMetadata(
                client,
                path = "profile",
                entryId = secondAsset!!.entryId,
                expectedStatus = HttpStatusCode.NotFound,
            )
        }

    @Test
    fun `can delete assets by path and order and limit`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val firstAsset = storeAssetMultipartSource(client, image, request, path = "profile").second
            val secondAsset = storeAssetMultipartSource(client, image, request, path = "profile").second
            val thirdAsset = storeAssetMultipartSource(client, image, request, path = "profile").second

            deleteAssetsAtPath(client, path = "profile", orderBy = OrderBy.CREATED, limit = 2)

            fetchAssetMetadata(client, path = "profile")!!.apply {
                entryId shouldBe firstAsset?.entryId
            }
            fetchAssetMetadata(
                client,
                path = "profile",
                entryId = secondAsset!!.entryId,
                expectedStatus = HttpStatusCode.NotFound,
            )
            fetchAssetMetadata(
                client,
                path = "profile",
                entryId = thirdAsset!!.entryId,
                expectedStatus = HttpStatusCode.NotFound,
            )
        }

    @Test
    fun `cannot supply invalid entryId when deleting asset`() =
        testInMemory {
            val client = createJsonClient()
            client.delete("/assets/profile/-/entry/notANumber").status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `cannot supply negative entryId when deleting asset`() =
        testInMemory {
            val client = createJsonClient()
            client.delete("/assets/profile/-/entry/-1").status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `can delete assets at path but not recursively`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val firstAsset = storeAssetMultipartSource(client, image, request, path = "user/123").second
            val secondAsset = storeAssetMultipartSource(client, image, request, path = "user/123").second
            val assetToNotDelete = storeAssetMultipartSource(client, image, request, path = "user/123/profile").second

            client.delete("/assets/user/123/-/all").status shouldBe HttpStatusCode.NoContent

            fetchAssetMetadata(client, "user/123", entryId = null, expectedStatus = HttpStatusCode.NotFound)
            fetchAssetMetadata(client, "user/123", firstAsset!!.entryId, expectedStatus = HttpStatusCode.NotFound)
            fetchAssetMetadata(client, "user/123", secondAsset!!.entryId, expectedStatus = HttpStatusCode.NotFound)

            fetchAssetMetadata(client, "user/123/profile", assetToNotDelete!!.entryId)
            fetchAssetMetadata(client, "user/123/profile")
        }

    @Test
    fun `can delete assets at path recursively`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            val control = storeAssetMultipartSource(client, image, request, path = "user").second
            val firstAsset = storeAssetMultipartSource(client, image, request, path = "user/123").second
            val secondAsset = storeAssetMultipartSource(client, image, request, path = "user/123").second
            val thirdAsset = storeAssetMultipartSource(client, image, request, path = "user/123/profile").second
            val fourthAsset = storeAssetMultipartSource(client, image, request, path = "user/123/profile/other").second

            client.delete("/assets/user/123/-/recursive").status shouldBe HttpStatusCode.NoContent

            fetchAssetMetadata(client, "user/123", entryId = null, expectedStatus = HttpStatusCode.NotFound)
            fetchAssetMetadata(client, "user/123", firstAsset!!.entryId, expectedStatus = HttpStatusCode.NotFound)
            fetchAssetMetadata(client, "user/123", secondAsset!!.entryId, expectedStatus = HttpStatusCode.NotFound)
            fetchAssetMetadata(client, "user/123/profile", thirdAsset!!.entryId, expectedStatus = HttpStatusCode.NotFound)
            fetchAssetMetadata(client, "user/123/profile/other", fourthAsset!!.entryId, expectedStatus = HttpStatusCode.NotFound)

            fetchAssetMetadata(client, "user")
            fetchAssetMetadata(client, "user", entryId = control!!.entryId)
        }

    @Test
    fun `can delete assets at path by labels`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val requestWithLabels =
                StoreAssetRequest(
                    alt = "an image",
                    labels = mapOf("phone" to "iphone"),
                )
            val requestWithoutLabels =
                StoreAssetRequest(
                    alt = "an image",
                )
            val firstAsset = storeAssetMultipartSource(client, image, requestWithLabels, path = "user/123").second
            val secondAsset = storeAssetMultipartSource(client, image, requestWithoutLabels, path = "user/123").second
            val control = storeAssetMultipartSource(client, image, requestWithLabels, path = "user/123/profile").second

            deleteAssetsAtPath(
                client = client,
                path = "user/123",
                all = true,
                labels = mapOf("phone" to "iphone"),
            )

            fetchAssetMetadata(client, "user/123", firstAsset!!.entryId, expectedStatus = HttpStatusCode.NotFound)
            fetchAssetMetadata(client, "user/123", secondAsset!!.entryId, expectedStatus = HttpStatusCode.OK)

            fetchAssetMetadata(client, "user/123/profile", control!!.entryId)
            fetchAssetMetadata(client, "user/123/profile")
        }

    @Test
    fun `can delete assets recursively at path by labels`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val requestWithLabels =
                StoreAssetRequest(
                    alt = "an image",
                    labels = mapOf("phone" to "iphone"),
                )
            val requestWithoutLabels = StoreAssetRequest()
            val control = storeAssetMultipartSource(client, image, requestWithLabels, path = "user").second
            val firstAsset = storeAssetMultipartSource(client, image, requestWithLabels, path = "user/123").second
            val secondAsset = storeAssetMultipartSource(client, image, requestWithoutLabels, path = "user/123").second
            val thirdAsset = storeAssetMultipartSource(client, image, requestWithLabels, path = "user/123/profile").second
            val fourthAsset = storeAssetMultipartSource(client, image, requestWithoutLabels, path = "user/123/profile/other").second

            deleteAssetsRecursivelyAtPath(
                client = client,
                path = "user/123",
                labels = mapOf("phone" to "iphone"),
            )

            fetchAssetMetadata(client, "user/123", entryId = firstAsset!!.entryId, expectedStatus = HttpStatusCode.NotFound)
            fetchAssetMetadata(client, "user/123", entryId = secondAsset!!.entryId, expectedStatus = HttpStatusCode.OK)
            fetchAssetMetadata(client, "user/123/profile", entryId = thirdAsset!!.entryId, expectedStatus = HttpStatusCode.NotFound)
            fetchAssetMetadata(client, "user/123/profile/other", entryId = fourthAsset!!.entryId, expectedStatus = HttpStatusCode.OK)

            fetchAssetMetadata(client, "user")
            fetchAssetMetadata(client, "user", entryId = control!!.entryId)
        }

    @Test
    fun `cannot set both entryId and mode when deleting assets`() =
        testInMemory {
            client.delete("/assets/user/123/-/entry/1/recursive").status shouldBe HttpStatusCode.BadRequest
        }
}
