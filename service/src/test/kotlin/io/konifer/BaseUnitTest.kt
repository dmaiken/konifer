package io.konifer

import io.konifer.domain.asset.Asset
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Variant
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.mockk.spyk
import java.time.LocalDateTime
import java.util.UUID

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

    protected suspend fun storeReadyAsset(
        uploadedAt: LocalDateTime,
        path: String = "/users/123",
        height: Int = 100,
        width: Int = 100,
        format: ImageFormat = ImageFormat.PNG,
        url: String? = null,
        alt: String? = "",
        objectStoreBucket: String = "bucket",
        objectStoreKey: String = "${UUID.randomUUID()}${format.extension}",
    ): Asset.Ready =
        storePersistedAsset(
            path = path,
            height = height,
            width = width,
            format = format,
            url = url,
            alt = alt,
            objectStoreBucket = objectStoreBucket,
            objectStoreKey = objectStoreKey,
        ).markReady(uploadedAt).also {
            assetRepository.markReady(it)
        }
}
