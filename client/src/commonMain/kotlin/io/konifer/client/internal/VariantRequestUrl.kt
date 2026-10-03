package io.konifer.client.internal

import io.konifer.client.assets.AbsoluteAssetSelection
import io.konifer.client.assets.AssetSelection
import io.konifer.client.assets.RelativeAssetSelection
import io.konifer.client.assets.fetch.EntryId
import io.konifer.client.assets.fetch.OrderBy
import io.konifer.client.assets.fetch.RequestedTransformation
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
