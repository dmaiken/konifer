package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.infrastructure.http.signature.HmacSigningAlgorithm
import io.konifer.infrastructure.http.signature.UrlSigner
import io.konifer.testInMemory
import io.konifer.util.storeAssetMultipartSource
import io.konifer.util.updateAsset
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PathBoundaryTest : BaseFunctionalTest() {
    @ParameterizedTest
    @ValueSource(strings = ["album-/photo", "album-", "album--/photo-"])
    fun `hyphenated paths support CRUD through generated entry URLs without changing neighbors`(path: String) =
        testInMemory {
            val image = ImageFactory.testImage().bytes
            val neighborPath = path.replace("-", "")
            val neighbor = storeAssetMultipartSource(client, image, StoreAssetRequest(alt = "neighbor"), path = neighborPath)
            val stored = storeAssetMultipartSource(client, image, StoreAssetRequest(alt = "target"), path = path)
            val location = checkNotNull(stored.first[HttpHeaders.Location])
            Url(location).encodedPath shouldBe "/assets/$path/-/entry/${stored.second!!.entryId}"
            val link = client.get(location).body<AssetLinkResponse>()
            link.alt shouldBe "target"
            client.get(link.url).bodyAsBytes().toList() shouldBe image.toList()
            val plain = client.get("/assets/$path").body<AssetLinkResponse>()
            plain.alt shouldBe "target"
            val info = client.get("$location/info")
            info.status shouldBe HttpStatusCode.OK
            info.body<AssetResponse>().alt shouldBe "target"
            updateAsset(client, location, StoreAssetRequest(alt = "updated"))
            client.get("$location/info").body<AssetResponse>().alt shouldBe "updated"
            client.delete(location).status shouldBe HttpStatusCode.NoContent
            client.get("$location/info").status shouldBe HttpStatusCode.NotFound
            client.get(neighbor.first[HttpHeaders.Location]!! + "/info").body<AssetResponse>().alt shouldBe "neighbor"
        }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `generated delivery links for hyphenated paths work with and without signing`(signed: Boolean) =
        testInMemory(
            """
            url-signing {
                enabled = $signed
                secret-key = secret
                algorithm = hmac_sha256
            }
            """.trimIndent(),
        ) {
            val image = ImageFactory.testImage().bytes
            val path = "album-/photo-"
            storeAssetMultipartSource(client, image, StoreAssetRequest(), path = path, verifyLocationHeader = !signed)
            val requestPath = "/assets/$path/-/link"
            val signature =
                if (signed) {
                    "?s=" + UrlSigner.sign(requestPath, emptyMap(), "secret", HmacSigningAlgorithm.HMAC_SHA256)
                } else {
                    ""
                }
            val response = client.get(requestPath + signature)
            response.status shouldBe HttpStatusCode.OK
            val link = response.body<AssetLinkResponse>()
            val content = client.get(link.url)
            content.status shouldBe HttpStatusCode.OK
            content.bodyAsBytes().toList() shouldBe image.toList()
        }

    @ParameterizedTest
    @ValueSource(strings = ["album/-", "album/-/", "album/-/info/-", "album/-/info/-/entry/42"])
    fun `malformed selector boundaries return bad request for all methods`(path: String) =
        testInMemory {
            val url = "/assets/$path"
            client.get(url).status shouldBe HttpStatusCode.BadRequest
            client.put(url).status shouldBe HttpStatusCode.BadRequest
            client.delete(url).status shouldBe HttpStatusCode.BadRequest
            storeAssetMultipartSource(
                client,
                ImageFactory.testImage().bytes,
                StoreAssetRequest(),
                path = path,
                expectedStatus = HttpStatusCode.BadRequest,
            )
        }
}
