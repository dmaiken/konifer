package io

import io.asset.handler.StoreAssetDto
import io.asset.model.AssetAndVariants
import io.asset.model.StoreAssetRequest
import io.asset.repository.InMemoryAssetRepository
import io.asset.store.PersistResult
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
    ): AssetAndVariants =
        assetRepository.store(
            StoreAssetDto(
                path = path,
                request =
                    StoreAssetRequest(
                        alt = "",
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
            ),
        )
}
