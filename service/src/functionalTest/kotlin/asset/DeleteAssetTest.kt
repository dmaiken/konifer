package asset

import asset.model.AssetResponse
import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import util.createJsonClient
import util.fetchAssetInfo
import util.storeAsset
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
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            storeAsset(client, image, request, path = "profile")

            client.get("/assets/profile?return=metadata").apply {
                status shouldBe HttpStatusCode.OK
            }
            client.delete("/assets/profile").status shouldBe HttpStatusCode.NoContent
            client.get("/assets/profile?return=metadata").apply {
                status shouldBe HttpStatusCode.NotFound
            }
            client.delete("/assets/profile").status shouldBe HttpStatusCode.NoContent
        }

    @Test
    fun `deleting asset by path causes next oldest asset to be returned when fetching by path`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val firstAsset = storeAsset(client, image, request, path = "profile")
            val secondAsset = storeAsset(client, image, request, path = "profile")

            client.get("/assets/profile?return=metadata").apply {
                status shouldBe HttpStatusCode.OK
                body<AssetResponse>().apply {
                    entryId shouldBe secondAsset?.entryId
                }
            }
            client.delete("/assets/profile").status shouldBe HttpStatusCode.NoContent
            client.get("/assets/profile?return=metadata").apply {
                status shouldBe HttpStatusCode.OK
                body<AssetResponse>().apply {
                    entryId shouldBe firstAsset?.entryId
                }
            }
        }

    @Test
    fun `can delete asset by path and entryId`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val firstAsset = storeAsset(client, image, request, path = "profile")
            val secondAsset = storeAsset(client, image, request, path = "profile")

            client.delete("/assets/profile?entryId=${firstAsset!!.entryId}").status shouldBe HttpStatusCode.NoContent
            client.get("/assets/profile?return=metadata").apply {
                status shouldBe HttpStatusCode.OK
                body<AssetResponse>().apply {
                    entryId shouldBe secondAsset?.entryId
                }
            }
            client.get("/assets/profile?return=metadata&entryId=${firstAsset.entryId}").apply {
                status shouldBe HttpStatusCode.NotFound
            }
        }

    @Test
    fun `cannot supply invalid entryId when deleting asset`() =
        testInMemory {
            val client = createJsonClient()
            client.delete("/assets/profile?entryId=notANumber").status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `cannot supply negative entryId when deleting asset`() =
        testInMemory {
            val client = createJsonClient()
            client.delete("/assets/profile?entryId=-1").status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `can delete assets at path but not recursively`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val firstAsset = storeAsset(client, image, request, path = "user/123")
            val secondAsset = storeAsset(client, image, request, path = "user/123")
            val assetToNotDelete = storeAsset(client, image, request, path = "user/123/profile")

            client.delete("/assets/user/123?mode=children").status shouldBe HttpStatusCode.NoContent

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
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val control = storeAsset(client, image, request, path = "user")
            val firstAsset = storeAsset(client, image, request, path = "user/123")
            val secondAsset = storeAsset(client, image, request, path = "user/123")
            val thirdAsset = storeAsset(client, image, request, path = "user/123/profile")
            val fourthAsset = storeAsset(client, image, request, path = "user/123/profile/other")

            client.delete("/assets/user/123?mode=recursive").status shouldBe HttpStatusCode.NoContent

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
            client.delete("/assets/user/123?mode=recursive&entryId=1").status shouldBe HttpStatusCode.BadRequest
        }
}
