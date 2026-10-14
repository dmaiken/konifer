package io.konifer.client.assets.fetch

import io.konifer.common.http.AttributesResponse
import io.konifer.common.http.LQIPResponse

/** Buffered variant bytes and metadata. Metadata is unavailable when following a delivery redirect. */
data class AssetContent(
    val bytes: ByteArray,
    val attributes: AttributesResponse? = null,
    val alt: String? = null,
    val lqip: LQIPResponse? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is AssetContent && bytes.contentEquals(other.bytes) &&
            attributes == other.attributes && alt == other.alt && lqip == other.lqip

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + attributes.hashCode()
        result = 31 * result + alt.hashCode()
        return 31 * result + lqip.hashCode()
    }
}

/** Metadata returned after streaming a variant. Unavailable fields are null for redirect delivery. */
data class AssetContentMetadata(
    val attributes: AttributesResponse? = null,
    val alt: String? = null,
    val lqip: LQIPResponse? = null,
)
