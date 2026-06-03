package io.konifer.common.asset

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object AssetClassParameterValues {
    const val IMAGE = "image"
}

@Serializable
enum class AssetClass {
    @SerialName(AssetClassParameterValues.IMAGE)
    IMAGE,
}
