package io.konifer.clientV2.internal

import io.konifer.clientV2.model.EntryId
import io.konifer.clientV2.model.OrderBy
import io.konifer.clientV2.model.RequestedTransformation
import io.konifer.clientV2.request.AbsoluteAssetSelection
import io.konifer.clientV2.request.AssetSelection
import io.konifer.clientV2.request.RelativeAssetSelection
import io.konifer.common.selector.ReturnFormat
import io.ktor.http.URLBuilder

internal fun URLBuilder.appendVariantRequest(
    asset: AssetSelection,
    returnFormat: ReturnFormat,
    transformation: RequestedTransformation,
) {
    val path =
        when (asset) {
            is AbsoluteAssetSelection -> asset.path
            is RelativeAssetSelection -> asset.path
            else -> error("Unsupported asset selection: ${asset::class}")
        }
    appendAssetPath(path)
    when (asset) {
        is AbsoluteAssetSelection -> {
            appendQuerySelectors(returnFormat, EntryId(asset.entryId))
        }

        is RelativeAssetSelection -> {
            appendQuerySelectors(returnFormat, OrderBy(asset.orderBy))
            appendLabels(asset.labels)
        }
    }
    appendTransformationParameters(transformation)
}
