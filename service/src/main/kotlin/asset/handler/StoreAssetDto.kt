package asset.handler

import asset.model.StoreAssetRequest
import asset.store.PersistResult
import image.model.Attributes
import image.model.LQIPs

class StoreAssetDto(
    val mimeType: String,
    val path: String,
    val request: StoreAssetRequest,
    val attributes: Attributes,
    val persistResult: PersistResult,
    val lqips: LQIPs,
)
