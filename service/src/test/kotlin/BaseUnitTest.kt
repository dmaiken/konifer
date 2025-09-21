package io

import asset.handler.StoreAssetDto
import asset.model.AssetAndVariants
import asset.model.StoreAssetRequest
import asset.repository.InMemoryAssetRepository
import asset.store.PersistResult
import asset.variant.VariantParameterGenerator
import image.model.Attributes
import image.model.ImageFormat
import image.model.LQIPs
import io.mockk.spyk

abstract class BaseUnitTest {
    protected val assetRepository = spyk(InMemoryAssetRepository(VariantParameterGenerator()))

    protected suspend fun storeAsset(
        path: String = "/users/123",
        height: Int = 100,
        width: Int = 100,
        format: ImageFormat = ImageFormat.PNG,
    ): AssetAndVariants =
        assetRepository.store(
            StoreAssetDto(
                mimeType = ImageFormat.PNG.mimeType,
                path = path,
                request =
                    StoreAssetRequest(
                        type = format.mimeType,
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
