package io.direkt

import io.direkt.asset.handler.AssetSource
import io.direkt.asset.handler.dto.StoreAssetDto
import io.direkt.asset.model.AssetAndVariants
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.asset.repository.InMemoryAssetRepository
import io.direkt.asset.store.PersistResult
import io.image.model.Attributes
import io.image.model.ImageFormat
import io.image.model.LQIPs
import io.mockk.spyk

abstract class BaseUnitTest {
    protected val assetRepository = spyk(InMemoryAssetRepository())

    protected suspend fun storeAsset(
        path: String = "/users/123",
        height: Int = 100,
        width: Int = 100,
        format: ImageFormat = ImageFormat.PNG,
        source: AssetSource = AssetSource.UPLOAD,
        url: String? = null,
        alt: String? = "",
    ): AssetAndVariants =
        assetRepository.store(
            StoreAssetDto(
                path = path,
                request =
                    StoreAssetRequest(
                        alt = alt,
                        url = url,
                    ),
                attributes =
                    Attributes(
                        width = width,
                        height = height,
                        format = format,
                    ),
                persistResult =
                    PersistResult(
                        bucket = "bucket",
                        key = "key",
                    ),
                lqips = LQIPs.NONE,
                source = source,
            ),
        )
}
