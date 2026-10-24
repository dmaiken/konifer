package io.konifer.asset.delete

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.client.assets.fetch.EntryId
import io.konifer.client.assets.fetch.Recursive
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class DeleteAssetTest : BaseFunctionalTest() {
    @Test
    fun `deleting asset that does not exist returns no content`() =
        testInMemory {
            client
                .delete("/assets/${UuidCreator.getRandomBasedFast()}?limit=-1")
                .apply {
                    status shouldBe HttpStatusCode.NoContent
                    bodyAsText() shouldBe ""
                }
            konifer()
                .deleteAsset(
                    path = UuidCreator.getRandomBasedFast().toString(),
                    limit = -1,
                ).shouldBeSuccessful()
        }

    @Test
    fun `can delete asset by path and entryId`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
            val firstAsset =
                konifer()
                    .storeAsset(
                        path = "profile",
                        bytes = image,
                        format = attributes.format,
                        request =
                            StoreAssetRequest(
                                alt = "an image",
                            ),
                    ).shouldBeSuccessful()
                    .body
            val secondAsset =
                konifer()
                    .storeAsset(
                        path = "profile",
                        bytes = image,
                        format = attributes.format,
                        request =
                            StoreAssetRequest(
                                alt = "an image",
                            ),
                    ).shouldBeSuccessful()
                    .body

            konifer()
                .deleteAsset(
                    path = "profile",
                    querySelectors = EntryId(firstAsset.entryId),
                ).shouldBeSuccessful()

            konifer()
                .fetchAssetInfo(
                    path = "profile",
                ).shouldBeSuccessful()
                .body.entryId shouldBe secondAsset.entryId
            konifer().fetchAssetInfo(
                path = "profile",
                querySelectors = EntryId(firstAsset.entryId),
            ) shouldHaveHttpError 404
        }

    @Test
    fun `cannot supply invalid entryId when deleting asset`() =
        testInMemory {
            client.delete("/assets/profile/-/entry/notANumber").status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `cannot supply negative entryId when deleting asset`() =
        testInMemory {
            client.delete("/assets/profile/-/entry/-1").status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `can delete assets at path but not recursively`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
            val firstAsset =
                konifer()
                    .storeAsset(
                        path = "user/123",
                        bytes = image,
                        format = attributes.format,
                        request =
                            StoreAssetRequest(
                                alt = "an image",
                            ),
                    ).shouldBeSuccessful()
                    .body
            val secondAsset =
                konifer()
                    .storeAsset(
                        path = "user/123",
                        bytes = image,
                        format = attributes.format,
                        request =
                            StoreAssetRequest(
                                alt = "an image",
                            ),
                    ).shouldBeSuccessful()
                    .body
            val assetToNotDelete =
                konifer()
                    .storeAsset(
                        path = "user/123/profile",
                        bytes = image,
                        format = attributes.format,
                        request =
                            StoreAssetRequest(
                                alt = "an image",
                            ),
                    ).shouldBeSuccessful()
                    .body

            konifer()
                .deleteAsset(
                    path = "user/123",
                    limit = -1,
                ).shouldBeSuccessful()

            konifer().fetchAssetInfo(
                path = "user/123",
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
            konifer().fetchAssetInfo(
                path = "user/123",
                querySelectors = EntryId(firstAsset.entryId),
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
            konifer().fetchAssetInfo(
                path = "user/123",
                querySelectors = EntryId(secondAsset.entryId),
            ) shouldHaveHttpError HttpStatusCode.NotFound.value

            konifer()
                .fetchAssetInfo(
                    path = "user/123/profile",
                    querySelectors = EntryId(assetToNotDelete.entryId),
                ).shouldBeSuccessful()
            konifer()
                .fetchAssetInfo(
                    path = "user/123/profile",
                ).shouldBeSuccessful()
        }

    @Test
    fun `can delete assets at path recursively`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
            val control =
                konifer()
                    .storeAsset(
                        path = "user",
                        bytes = image,
                        format = attributes.format,
                        request = StoreAssetRequest(),
                    ).shouldBeSuccessful()
                    .body
            val firstAsset =
                konifer()
                    .storeAsset(
                        path = "user/123",
                        bytes = image,
                        format = attributes.format,
                        request = StoreAssetRequest(),
                    ).shouldBeSuccessful()
                    .body
            val secondAsset =
                konifer()
                    .storeAsset(
                        path = "user/123",
                        bytes = image,
                        format = attributes.format,
                        request = StoreAssetRequest(),
                    ).shouldBeSuccessful()
                    .body
            val thirdAsset =
                konifer()
                    .storeAsset(
                        path = "user/123/profile",
                        bytes = image,
                        format = attributes.format,
                        request = StoreAssetRequest(),
                    ).shouldBeSuccessful()
                    .body
            val fourthAsset =
                konifer()
                    .storeAsset(
                        path = "user/123/profile/other",
                        bytes = image,
                        format = attributes.format,
                        request = StoreAssetRequest(),
                    ).shouldBeSuccessful()
                    .body

            konifer()
                .deleteAsset(
                    path = "user/123",
                    querySelectors = Recursive,
                ).shouldBeSuccessful()

            konifer().fetchAssetInfo(
                path = "user/123",
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
            konifer().fetchAssetInfo(
                path = "user/123",
                querySelectors = EntryId(firstAsset.entryId),
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
            konifer().fetchAssetInfo(
                path = "user/123",
                querySelectors = EntryId(secondAsset.entryId),
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
            konifer().fetchAssetInfo(
                path = "user/123/profile",
                querySelectors = EntryId(thirdAsset.entryId),
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
            konifer().fetchAssetInfo(
                path = "user/123/profile/other",
                querySelectors = EntryId(fourthAsset.entryId),
            ) shouldHaveHttpError HttpStatusCode.NotFound.value

            konifer()
                .fetchAssetInfo(
                    path = "user",
                ).shouldBeSuccessful()
            konifer()
                .fetchAssetInfo(
                    path = "user",
                    querySelectors = EntryId(control.entryId),
                ).shouldBeSuccessful()
        }

    @Test
    fun `can delete assets at path by labels`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
            val requestWithLabels =
                StoreAssetRequest(
                    alt = "an image",
                    labels = mapOf("phone" to "iphone"),
                )
            val requestWithoutLabels =
                StoreAssetRequest(
                    alt = "an image",
                )
            val control =
                konifer()
                    .storeAsset(
                        path = "user/123/profile",
                        bytes = image,
                        format = attributes.format,
                        request = requestWithLabels,
                    ).shouldBeSuccessful()
                    .body
            val firstAsset =
                konifer()
                    .storeAsset(
                        path = "user/123",
                        bytes = image,
                        format = attributes.format,
                        request = requestWithLabels,
                    ).shouldBeSuccessful()
                    .body
            val secondAsset =
                konifer()
                    .storeAsset(
                        path = "user/123",
                        bytes = image,
                        format = attributes.format,
                        request = requestWithoutLabels,
                    ).shouldBeSuccessful()
                    .body

            konifer().deleteAsset(
                path = "user/123",
                limit = -1,
                labels = mapOf("phone" to "iphone"),
            )

            konifer().fetchAssetInfo(
                path = "user/123",
                querySelectors = EntryId(firstAsset.entryId),
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
            konifer()
                .fetchAssetInfo(
                    path = "user/123",
                    querySelectors = EntryId(secondAsset.entryId),
                ).shouldBeSuccessful()

            konifer()
                .fetchAssetInfo(
                    path = "user/123/profile",
                    querySelectors = EntryId(control.entryId),
                ).shouldBeSuccessful()
            konifer()
                .fetchAssetInfo(
                    path = "user/123/profile",
                ).shouldBeSuccessful()
        }

    @Test
    fun `can delete assets recursively at path by labels`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
            val requestWithLabels =
                StoreAssetRequest(
                    alt = "an image",
                    labels = mapOf("phone" to "iphone"),
                )
            val requestWithoutLabels = StoreAssetRequest()
            val control =
                konifer()
                    .storeAsset(
                        path = "user",
                        bytes = image,
                        format = attributes.format,
                        request = requestWithLabels,
                    ).shouldBeSuccessful()
                    .body
            val firstAsset =
                konifer()
                    .storeAsset(
                        path = "user/123",
                        bytes = image,
                        format = attributes.format,
                        request = requestWithLabels,
                    ).shouldBeSuccessful()
                    .body
            val secondAsset =
                konifer()
                    .storeAsset(
                        path = "user/123",
                        bytes = image,
                        format = attributes.format,
                        request = requestWithoutLabels,
                    ).shouldBeSuccessful()
                    .body
            val thirdAsset =
                konifer()
                    .storeAsset(
                        path = "user/123/profile",
                        bytes = image,
                        format = attributes.format,
                        request = requestWithLabels,
                    ).shouldBeSuccessful()
                    .body
            val fourthAsset =
                konifer()
                    .storeAsset(
                        path = "user/123/profile/other",
                        bytes = image,
                        format = attributes.format,
                        request = requestWithoutLabels,
                    ).shouldBeSuccessful()
                    .body

            konifer()
                .deleteAsset(
                    path = "user/123",
                    labels = mapOf("phone" to "iphone"),
                    querySelectors = Recursive,
                ).shouldBeSuccessful()

            konifer().fetchAssetInfo(
                path = "user/123",
                querySelectors = EntryId(firstAsset.entryId),
            ) shouldHaveHttpError HttpStatusCode.NotFound.value
            konifer()
                .fetchAssetInfo(
                    path = "user/123",
                    querySelectors = EntryId(secondAsset.entryId),
                ).shouldBeSuccessful()
            konifer().fetchAssetInfo(
                path = "user/123/profile",
                querySelectors = EntryId(thirdAsset.entryId),
            ) shouldHaveHttpError 404
            konifer()
                .fetchAssetInfo(
                    path = "user/123/profile/other",
                    querySelectors = EntryId(fourthAsset.entryId),
                ).shouldBeSuccessful()

            konifer()
                .fetchAssetInfo(
                    path = "user",
                    querySelectors = EntryId(control.entryId),
                ).shouldBeSuccessful()
            konifer()
                .fetchAssetInfo(
                    path = "user",
                ).shouldBeSuccessful()
        }

    @Test
    fun `cannot set both entryId and mode when deleting assets`() =
        testInMemory {
            client.delete("/assets/user/123/-/entry/1/recursive").status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `invalid deletion requests leave every asset intact`() =
        testInMemory {
            val (image, _) = ImageFactory.testImage()
            val first = storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile").second!!
            val second = storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile").second!!
            val invalidRequests =
                listOf(
                    "/assets/profile",
                    "/assets/profile?limit=1",
                    "/assets/profile?limit=20",
                    "/assets/profile?limit=0",
                    "/assets/profile?limit=-2",
                    "/assets/profile?limit=abc",
                    "/assets/profile?limit=",
                    "/assets/profile?limit=2147483648",
                    "/assets/profile?limit=-2147483649",
                    "/assets/profile?limit=-1&limit=1",
                    "/assets/profile/-/new",
                    "/assets/profile/-/modified",
                    "/assets/profile/-/new?limit=-1",
                    "/assets/profile/-/modified?limit=-1",
                    "/assets/profile/-/entry/${second.entryId}?limit=1",
                    "/assets/profile/-/entry/${second.entryId}?limit=-1",
                    "/assets/profile/-/recursive?limit=1",
                    "/assets/profile/-/recursive?limit=-1",
                )
            invalidRequests.forEach { url ->
                client.delete(url).status shouldBe HttpStatusCode.BadRequest
                client
                    .get("/assets/profile/-/entries")
                    .body<AssetEntriesResponse>()
                    .entries
                    .map { it.entryId } shouldBe
                    listOf(second.entryId, first.entryId)
            }
        }

    @Test
    fun `entry deletion can be retried after another upload without deleting it`() =
        testInMemory {
            val (image, _) = ImageFactory.testImage()
            val first = storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile").second!!
            val second = storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile").second!!
            val url = "/assets/profile/-/entry/${second.entryId}"
            client.delete(url).status shouldBe HttpStatusCode.NoContent
            client.delete(url).status shouldBe HttpStatusCode.NoContent

            val replacement = storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile").second!!
            client.delete(url).status shouldBe HttpStatusCode.NoContent
            client
                .get("/assets/profile/-/entries")
                .body<AssetEntriesResponse>()
                .entries
                .map { it.entryId } shouldBe
                listOf(replacement.entryId, first.entryId)
        }

    @Test
    fun `bulk deletions can be repeated when nothing remains`() =
        testInMemory {
            val (image, _) = ImageFactory.testImage()
            storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile")
            storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile/child")
            repeat(2) {
                client.delete("/assets/profile?limit=-1").status shouldBe HttpStatusCode.NoContent
                client.get("/assets/profile/-/entries").body<AssetEntriesResponse>().entries shouldBe emptyList()
            }
            client
                .get("/assets/profile/child/-/entries")
                .body<AssetEntriesResponse>()
                .entries.size shouldBe 1
            repeat(2) {
                client.delete("/assets/profile/-/recursive").status shouldBe HttpStatusCode.NoContent
                client.get("/assets/profile/child/-/entries").body<AssetEntriesResponse>().entries shouldBe emptyList()
            }
        }
}
