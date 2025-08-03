package asset

import asset.model.StoreAssetRequest
import config.testInMemory
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import util.createJsonClient
import util.fetchAssetInfo
import util.fetchAssetsInfo
import util.storeAsset
import java.util.UUID

class FetchAssetInfoTest {
    @Test
    fun `getting all asset info with path returns all info`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
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
            fetchAssetInfo(client, "profile")!!.apply {
                entryId shouldBe entryIds[1]
            }

            fetchAssetsInfo(client, path = "profile", limit = 10).apply {
                size shouldBe 2
                get(0).entryId shouldBe entryIds[1]
                get(1).entryId shouldBe entryIds[0]
            }
        }

    @Test
    fun `fetching info of asset that does not exist returns not found`() =
        testInMemory {
            val client = createJsonClient()
            fetchAssetInfo(client, UUID.randomUUID().toString(), expectedStatus = HttpStatusCode.NotFound)
        }
}
