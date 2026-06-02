package io.konifer.common.asset

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object AssetSourceParameterValues {
    const val UPLOAD = "upload"
    const val URL = "url"
}

@Serializable
enum class AssetSource {
    @SerialName(AssetSourceParameterValues.UPLOAD)
    UPLOAD,

    @SerialName(AssetSourceParameterValues.URL)
    URL,
}
