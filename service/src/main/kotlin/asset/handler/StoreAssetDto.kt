package asset.handler

import asset.model.StoreAssetRequest
import asset.store.PersistResult
import image.model.ImageAttributes
import image.model.LQIPs

class StoreAssetDto(
    val mimeType: String,
    val treePath: String,
    val request: StoreAssetRequest,
    val imageAttributes: ImageAttributes,
    val persistResult: PersistResult,
    val lqips: LQIPs,
)
