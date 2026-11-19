package io.konifer.common.asset

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object AssetSourceParameterValues {
    const val UPLOAD = "upload"
    const val URL = "url"
    const val ARN = "arn"
}

@Serializable
@GhostSerialization
enum class AssetSource {
    @SerialName(AssetSourceParameterValues.UPLOAD)
    UPLOAD,

    @SerialName(AssetSourceParameterValues.URL)
    URL,

    @SerialName(AssetSourceParameterValues.ARN)
    ARN,
}
