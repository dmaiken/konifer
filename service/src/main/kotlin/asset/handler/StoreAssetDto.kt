package asset.handler

import asset.model.StoreAssetRequest
import asset.store.PersistResult
import image.model.ImageAttributes

class StoreAssetDto(
    val mimeType: String,
    val treePath: String,
    val request: StoreAssetRequest,
    val imageAttributes: ImageAttributes,
    val persistResult: PersistResult,
)
