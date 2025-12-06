package io.direkt

import io.direkt.domain.asset.AssetSource
import io.direkt.asset.handler.dto.StoreAssetDto
import io.direkt.asset.model.AssetAndVariants
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.infrastructure.database.inmemory.InMemoryAssetRepository
import io.direkt.domain.ports.PersistResult
import io.direkt.domain.image.Attributes
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.LQIPs
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
