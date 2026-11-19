package io.konifer.common.asset

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object AssetClassParameterValues {
    const val IMAGE = "image"
}

@Serializable
@GhostSerialization
enum class AssetClass {
    @SerialName(AssetClassParameterValues.IMAGE)
    IMAGE,
}
