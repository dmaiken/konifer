package asset

import asset.model.AssetResponse
import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import util.createJsonClient
import util.storeAsset
import java.util.UUID

class FetchAssetInfoTest {
    @Test
    fun `getting all asset info with path returns all info`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/img.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                )
            val entryIds = mutableListOf<Long>()
            repeat(2) {
                storeAsset(client, image, request, path = "profile")?.apply {
                    entryIds.add(entryId)
                }
            }
            entryIds shouldHaveSize 2
            client.get("/assets/profile?return=metadata").apply {
                status shouldBe HttpStatusCode.OK
                body<AssetResponse>().apply {
                    entryIds[1] shouldBe entryId
                }
            }

            client.get("/assets/profile?return=metadata&all=true").apply {
                status shouldBe HttpStatusCode.OK
                body<List<AssetResponse>>().apply {
                    size shouldBe 2
                    get(0).entryId shouldBe entryIds[1]
                    get(1).entryId shouldBe entryIds[0]
                }
            }
        }

    @Test
    fun `fetching info of asset that does not exist returns not found`() =
        testInMemory {
            val client = createJsonClient()
            client.get("/assets/${UUID.randomUUID()}?return=metadata").apply {
                status shouldBe HttpStatusCode.NotFound
            }
        }
}
