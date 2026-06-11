package io.konifer.asset

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.client.EntryId
import io.konifer.client.OrderBy
import io.konifer.client.Recursive
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.selector.Order
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.konifer.util.fetchAssetInfo
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class DeleteAssetTest : BaseFunctionalTest() {
    @Test
    fun `deleting asset that does not exist returns no content`() =
        testInMemory {
            client
                .delete("/assets/${UuidCreator.getRandomBasedFast()}")
                .apply {
                    status shouldBe HttpStatusCode.NoContent
                    bodyAsText() shouldBe ""
                }
            konifer()
                .deleteAsset(
                    path = UuidCreator.getRandomBasedFast().toString(),
                ).shouldBeSuccessful()
        }

    @Test
    fun `can delete asset by path`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()
            konifer().storeAsset(
                path = "profile",
                bytes = image,
                format = attributes.format,
                request =
                    StoreAssetRequest(
                        alt = "an image",
                    ),
            )
            konifer()
                .fetchAssetInfo(
                    path = "profile",
                ).shouldBeSuccessful()
            konifer()
                .deleteAsset(
                    path = "profile",
                ).shouldBeSuccessful()
            konifer().fetchAssetInfo(
                path = "profile",
            ) shouldHaveHttpError 404

            konifer()
                .deleteAsset(
                    path = "profile",
                ).shouldBeSuccessful()
        }

    @Test
    fun `deleting asset by path causes next oldest asset to be returned when fetching by path`() =
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

            fetchAssetInfo(client, path = "profile")!!.apply {
                entryId shouldBe secondAsset.entryId
            }
            konifer()
                .fetchAssetInfo(
                    path = "profile",
                ).shouldBeSuccessful()
            konifer()
                .deleteAsset(
                    path = "profile",
                ).shouldBeSuccessful()
            konifer()
                .fetchAssetInfo(
                    path = "profile",
                ).shouldBeSuccessful()
                .body.entryId shouldBe firstAsset.entryId
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
    fun `can delete assets by path and order`() =
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
                    limit = 1,
                    querySelectors = OrderBy(Order.NEW),
                ).shouldBeSuccessful()
            konifer()
                .fetchAssetInfo(
                    path = "profile",
                ).shouldBeSuccessful()
                .body.entryId shouldBe firstAsset.entryId
            konifer().fetchAssetInfo(
                path = "profile",
                querySelectors = EntryId(secondAsset.entryId),
            ) shouldHaveHttpError 404
        }

    @Test
    fun `can delete assets by path and order and limit`() =
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
            val thirdAsset =
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
                    querySelectors = OrderBy(Order.NEW),
                    limit = 2,
                ).shouldBeSuccessful()

            konifer()
                .fetchAssetInfo(
                    path = "profile",
                ).shouldBeSuccessful()
                .body.entryId shouldBe firstAsset.entryId
            konifer().fetchAssetInfo(
                path = "profile",
                querySelectors = EntryId(secondAsset.entryId),
            ) shouldHaveHttpError 404
            konifer().fetchAssetInfo(
                path = "profile",
                querySelectors = EntryId(thirdAsset.entryId),
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
                    querySelectors = Recursive(),
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
                    querySelectors = Recursive(),
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
}
