package io.asset

import io.asset.model.StoreAssetRequest
import io.config.testInMemory
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.util.assertAssetDoesNotExist
import io.util.createJsonClient
import io.util.deleteAsset
import io.util.fetchAssetInfo
import io.util.storeAssetMultipart
import org.junit.jupiter.api.Test
import java.util.UUID

class DeleteAssetTest {
    @Test
    fun `deleting asset that does not exist returns no content`() =
        testInMemory {
            val client = createJsonClient()
            client.delete("/assets/${UUID.randomUUID()}")
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
                    type = "image/png",
                    alt = "an image",
                )
            storeAssetMultipart(client, image, request, path = "profile")
            fetchAssetInfo(client, path = "profile")
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
                    type = "image/png",
                    alt = "an image",
                )
            val firstAsset = storeAssetMultipart(client, image, request, path = "profile")
            val secondAsset = storeAssetMultipart(client, image, request, path = "profile")

            fetchAssetInfo(client, path = "profile")!!.apply {
                entryId shouldBe secondAsset?.entryId
            }
            deleteAsset(client, path = "profile")
            fetchAssetInfo(client, path = "profile")!!.apply {
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
                    type = "image/png",
                    alt = "an image",
                )
            val firstAsset = storeAssetMultipart(client, image, request, path = "profile")
            val secondAsset = storeAssetMultipart(client, image, request, path = "profile")

            deleteAsset(client, path = "profile", entryId = firstAsset!!.entryId)

            fetchAssetInfo(client, path = "profile")!!.apply {
                entryId shouldBe secondAsset?.entryId
            }
            fetchAssetInfo(
                client,
                path = "profile",
                entryId = firstAsset.entryId,
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
                    type = "image/png",
                    alt = "an image",
                )
            val firstAsset = storeAssetMultipart(client, image, request, path = "user/123")
            val secondAsset = storeAssetMultipart(client, image, request, path = "user/123")
            val assetToNotDelete = storeAssetMultipart(client, image, request, path = "user/123/profile")

            client.delete("/assets/user/123/-/children").status shouldBe HttpStatusCode.NoContent

            fetchAssetInfo(client, "user/123", entryId = null, HttpStatusCode.NotFound)
            fetchAssetInfo(client, "user/123", firstAsset!!.entryId, HttpStatusCode.NotFound)
            fetchAssetInfo(client, "user/123", secondAsset!!.entryId, HttpStatusCode.NotFound)

            fetchAssetInfo(client, "user/123/profile", assetToNotDelete!!.entryId)
            fetchAssetInfo(client, "user/123/profile")
        }

    @Test
    fun `can delete assets at path recursively`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val control = storeAssetMultipart(client, image, request, path = "user")
            val firstAsset = storeAssetMultipart(client, image, request, path = "user/123")
            val secondAsset = storeAssetMultipart(client, image, request, path = "user/123")
            val thirdAsset = storeAssetMultipart(client, image, request, path = "user/123/profile")
            val fourthAsset = storeAssetMultipart(client, image, request, path = "user/123/profile/other")

            client.delete("/assets/user/123/-/recursive").status shouldBe HttpStatusCode.NoContent

            fetchAssetInfo(client, "user/123", entryId = null, HttpStatusCode.NotFound)
            fetchAssetInfo(client, "user/123", firstAsset!!.entryId, HttpStatusCode.NotFound)
            fetchAssetInfo(client, "user/123", secondAsset!!.entryId, HttpStatusCode.NotFound)
            fetchAssetInfo(client, "user/123/profile", thirdAsset!!.entryId, HttpStatusCode.NotFound)
            fetchAssetInfo(client, "user/123/profile/other", fourthAsset!!.entryId, HttpStatusCode.NotFound)

            fetchAssetInfo(client, "user")
            fetchAssetInfo(client, "user", entryId = control!!.entryId)
        }

    @Test
    fun `cannot set both entryId and mode when deleting assets`() =
        testInMemory {
            client.delete("/assets/user/123/-/entry/1/recursive").status shouldBe HttpStatusCode.BadRequest
        }
}
