package io.konifer.infrastructure.datastore

import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetData
import io.konifer.domain.asset.AssetId
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantData
import io.konifer.infrastructure.StoreAssetRequest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.temporal.ChronoUnit
import java.util.UUID

fun createPendingAsset(
    path: String = "/users/123",
    alt: String = "an image",
    labels: Map<String, String> =
        mapOf(
            "phone" to "iphone",
            "customer" to "vip",
        ),
    tags: Set<String> =
        setOf(
            "scary",
            "spooky",
        ),
    url: String? = null,
    attributes: Attributes =
        Attributes(
            width = 100,
            height = 100,
            format = ImageFormat.PNG,
        ),
    objectStoreBucket: String = "bucket",
    objectStoreKey: String = "${UUID.randomUUID()}${attributes.format.extension}",
    lqips: LQIPs = LQIPs.NONE,
): Asset.Pending =
    Asset.New
        .fromHttpRequest(
            path = path,
            request =
                StoreAssetRequest(
                    alt = alt,
                    labels = labels,
                    tags = tags,
                    url = url,
                ),
        ).let {
            it.markPending(
                originalVariant =
                    Variant.Pending.originalVariant(
                        attributes = attributes,
                        objectStoreBucket = objectStoreBucket,
                        objectStoreKey = objectStoreKey,
                        lqip = lqips,
                        assetId = it.id,
                    ),
            )
        }

fun createPendingVariant(
    assetId: AssetId,
    attributes: Attributes =
        Attributes(
            width = 150,
            height = 100,
            format = ImageFormat.PNG,
        ),
    objectStoreBucket: String = "bucket",
    transformation: Transformation,
    objectStoreKey: String = "${UUID.randomUUID()}${attributes.format.extension}",
    lqip: LQIPs = LQIPs.NONE,
): Variant.Pending =
    Variant.Pending.newVariant(
        assetId = assetId,
        attributes = attributes,
        objectStoreBucket = objectStoreBucket,
        objectStoreKey = objectStoreKey,
        lqip = lqip,
        transformation = transformation,
    )

fun assertFetchedAgainstAggregate(
    fetched: AssetData?,
    asset: Asset,
    validateTransformations: Boolean,
) {
    fetched shouldNotBe null
    fetched!!.id shouldBe asset.id
    fetched.tags shouldBe asset.tags
    fetched.labels shouldBe asset.labels
    fetched.alt shouldBe asset.alt
    fetched.path shouldBe asset.path
    fetched.entryId shouldBe asset.entryId
    fetched.source shouldBe asset.source
    fetched.sourceUrl shouldBe asset.sourceUrl
    fetched.createdAt shouldBe asset.createdAt
    fetched.modifiedAt.truncatedTo(ChronoUnit.MILLIS) shouldBe asset.modifiedAt.truncatedTo(ChronoUnit.MILLIS)

    if (validateTransformations) {
        fetched.variants shouldHaveSize asset.variants.size
        fetched.variants.forEachIndexed { index, variant ->
            asset.variants[index].also {
                assertFetchedVariantAgainstAggregate(variant, it)
            }
        }
    }
}

fun assertFetchedVariantAgainstAggregate(
    fetched: VariantData?,
    variant: Variant,
) {
    fetched shouldNotBe null
    fetched!!.id shouldBe variant.id
    fetched.createdAt shouldBe variant.createdAt
    fetched.attributes shouldBe variant.attributes
    fetched.transformation shouldBe variant.transformation
    fetched.isOriginalVariant shouldBe variant.isOriginalVariant
    fetched.uploadedAt?.truncatedTo(ChronoUnit.MILLIS) shouldBe variant.uploadedAt?.truncatedTo(ChronoUnit.MILLIS)
    fetched.objectStoreKey shouldBe variant.objectStoreKey
    fetched.objectStoreBucket shouldBe variant.objectStoreBucket
    fetched.lqips shouldBe variant.lqips
}
