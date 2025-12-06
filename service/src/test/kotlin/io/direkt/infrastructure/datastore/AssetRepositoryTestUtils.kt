package io.direkt.infrastructure.datastore

import io.direkt.asset.handler.dto.StoreAssetDto
import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetSource
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.ports.PersistResult
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Variant
import io.direkt.infrastructure.StoreAssetRequest
import java.util.UUID

fun createPendingAsset(
    path: String = "/users/123",
    alt: String = "an image",
    labels: Map<String, String> = mapOf(
        "phone" to "iphone",
        "customer" to "vip",
    ),
    tags: Set<String> = setOf(
        "scary",
        "spooky",
    ),
    url: String? = null,
    attributes: Attributes = Attributes(
        width = 100,
        height = 100,
        format = ImageFormat.PNG,
    ),
    objectStoreBucket: String = "bucket",
    objectStoreKey: String = "${UUID.randomUUID()}${attributes.format.extension}",
    lqips: LQIPs = LQIPs.NONE
): Asset.Pending = Asset.New.fromHttpRequest(
    path = path,
    request = StoreAssetRequest(
        alt = alt,
        labels = labels,
        tags = tags,
        url = url,
    )
).markPending(
    originalVariant = Variant.Pending.originalVariant(
        attributes = attributes,
        objectStoreBucket = objectStoreBucket,
        objectStoreKey = objectStoreKey,
        lqip = lqips
    )
)

fun createAssetDto(
    treePath: String,
    labels: Map<String, String> =
        mapOf(
            "phone" to "iphone",
            "customer" to "vip",
        ),
    source: AssetSource = AssetSource.UPLOAD,
    url: String? = null,
): StoreAssetDto =
    StoreAssetDto(
        path = treePath,
        request =
            StoreAssetRequest(
                alt = "an image",
                labels = labels,
                tags =
                    setOf(
                        "scary",
                        "spooky",
                    ),
                url = url,
            ),
        attributes =
            Attributes(
                width = 100,
                height = 100,
                format = ImageFormat.PNG,
            ),
        persistResult =
            PersistResult(
                key = UUID.randomUUID().toString(),
                bucket = "bucket",
            ),
        lqips = LQIPs.NONE,
        source = source,
    )
