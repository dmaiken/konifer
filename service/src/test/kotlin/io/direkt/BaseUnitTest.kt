package io.direkt

import io.direkt.domain.asset.Asset
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Variant
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.mockk.spyk
import java.util.UUID

abstract class BaseUnitTest {
    protected val assetRepository = spyk(InMemoryAssetRepository())

    protected suspend fun storeAsset(
        path: String = "/users/123",
        height: Int = 100,
        width: Int = 100,
        format: ImageFormat = ImageFormat.PNG,
        url: String? = null,
        alt: String? = "",
        objectStoreBucket: String = "bucket",
        objectStoreKey: String = "${UUID.randomUUID()}${format.extension}",
    ): Asset.PendingPersisted =
        assetRepository.storeNew(
            asset =
                Asset.New
                    .fromHttpRequest(
                        path = path,
                        request =
                            StoreAssetRequest(
                                alt = alt,
                                url = url,
                            ),
                    ).let {
                        it.markPending(
                            originalVariant =
                                Variant.Pending.originalVariant(
                                    assetId = it.id,
                                    attributes =
                                        Attributes(
                                            height = height,
                                            width = width,
                                            format = format,
                                        ),
                                    objectStoreBucket = objectStoreBucket,
                                    objectStoreKey = objectStoreKey,
                                    lqip = LQIPs.NONE,
                                ),
                        )
                    },
        )
}
