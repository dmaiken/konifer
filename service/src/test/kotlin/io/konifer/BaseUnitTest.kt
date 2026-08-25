package io.konifer

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Variant
import io.konifer.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.mockk.spyk
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

abstract class BaseUnitTest {
    protected val assetRepository = spyk(InMemoryAssetRepository())

    protected suspend fun storePersistedAsset(
        path: String = "/users/123",
        height: Int = 100,
        width: Int = 100,
        format: ImageFormat = ImageFormat.PNG,
        url: String? = null,
        alt: String? = "",
        objectStoreBucket: String = "bucket",
        objectStoreKey: String = "${UuidCreator.getRandomBasedFast()}${format.extension}",
        orientation: Int = 1,
    ): Asset.Ready {
        val newAsset =
            Asset.New
                .fromHttpRequest(
                    path = path,
                    request =
                        StoreAssetRequest(
                            alt = alt,
                            url = url,
                        ),
                )
        val originalVariant =
            Variant.Pending.originalVariant(
                assetId = newAsset.id,
                attributes =
                    Attributes(
                        height = height.toDimension(),
                        width = width.toDimension(),
                        format = format,
                        orientation = orientation,
                        colorSpace = ColorSpace.SRGB,
                    ),
                objectStoreBucket = objectStoreBucket,
                objectStoreKey = objectStoreKey,
                lqip = LQIPs.NONE,
            )
        val asset =
            assetRepository
                .storeNew(
                    asset =
                        newAsset.markPending(
                            originalVariant = originalVariant,
                            additionalLabels = AssetLabels.empty,
                        ),
                ).markReady(LocalDateTime.now(UTC))
        assetRepository.markReady(asset)
        return asset
    }
}
