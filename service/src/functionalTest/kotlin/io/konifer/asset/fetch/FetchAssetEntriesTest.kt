package io.konifer.asset.fetch

import io.konifer.BaseFunctionalTest
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.testInMemory
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class FetchAssetEntriesTest : BaseFunctionalTest() {
    @Test
    fun `entries returns an envelope for one match and honors labels and order`() =
        testInMemory {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val first = storeAssetMultipartSource(client, image, StoreAssetRequest(labels = mapOf("camera" to "phone"))).second!!
            storeAssetMultipartSource(client, image, StoreAssetRequest(labels = mapOf("camera" to "dslr")))
            val newestPhone = storeAssetMultipartSource(client, image, StoreAssetRequest(labels = mapOf("camera" to "phone"))).second!!

            val response =
                client.get("/assets/profile/-/new/entries") {
                    parameter("limit", "1")
                    parameter("camera", "phone")
                }

            response.status shouldBe HttpStatusCode.OK
            response.body<AssetEntriesResponse>().entries.map { it.entryId } shouldBe listOf(newestPhone.entryId)

            val modified =
                client.get("/assets/profile/-/modified/entries") {
                    parameter("limit", "2")
                    parameter("camera", "phone")
                }
            modified.status shouldBe HttpStatusCode.OK
            modified.body<AssetEntriesResponse>().entries.map { it.entryId } shouldBe listOf(newestPhone.entryId, first.entryId)
        }

    @Test
    fun `entries returns an empty envelope when no assets match`() =
        testInMemory {
            val response = client.get("/assets/profile/does-not-exist/-/new/entries?limit=1")

            response.status shouldBe HttpStatusCode.OK
            response.body<AssetEntriesResponse>().entries shouldBe emptyList()
        }

    @Test
    fun `entries rejects transformations and entry ID selection`() =
        testInMemory {
            client.get("/assets/profile/-/new/entries?w=100").status shouldBe HttpStatusCode.BadRequest
            client.get("/assets/profile/-/new/entries?format=png").status shouldBe HttpStatusCode.BadRequest
            client.get("/assets/profile/-/entry/42/entries").status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `info remains singular and rejects a list limit`() =
        testInMemory {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val stored = storeAssetMultipartSource(client, image, StoreAssetRequest()).second!!

            client.get("/assets/profile/-/new/info").body<AssetResponse>().entryId shouldBe stored.entryId
            client.get("/assets/profile/-/new/info?limit=2").status shouldBe HttpStatusCode.BadRequest
        }
}
