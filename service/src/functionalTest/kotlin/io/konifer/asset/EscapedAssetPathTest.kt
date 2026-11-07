package io.konifer.asset

import io.konifer.BasePostgresTestContainersTest
import io.konifer.ImageFactory
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.testPostgres
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class EscapedAssetPathTest : BasePostgresTestContainersTest() {
    @Test
    fun `punctuation paths survive Postgres storage and generated URLs`() =
        testPostgres(postgres) {
            val image = ImageFactory.testImage().bytes
            val paths = listOf("users/alice@example.com", "tenant:123/v1.2", "a.b", "a/b", "a_2eb", "A_1/.~!$'()*+=@:-")
            paths.forEach { path ->
                val (_, asset) = storeAssetMultipartSource(client, image, StoreAssetRequest(alt = path), path = path)
                checkNotNull(asset).entryId shouldBe 0L
            }
            paths.forEach { path ->
                val metadata = client.get("/assets/$path/-/info")
                metadata.status shouldBe HttpStatusCode.OK
                metadata.body<AssetResponse>().alt shouldBe path
                val response = client.get("/assets/$path/-/link")
                response.status shouldBe HttpStatusCode.OK
                val link = response.body<AssetLinkResponse>()
                link.alt shouldBe path
                val content = client.get(link.url)
                content.status shouldBe HttpStatusCode.OK
                content.bodyAsBytes().toList() shouldBe image.toList()
            }
        }
}
