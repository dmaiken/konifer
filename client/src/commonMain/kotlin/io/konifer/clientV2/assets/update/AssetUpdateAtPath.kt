package io.konifer.clientV2.assets.update

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.RequestInfrastructure
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest

/**
 * An immutable metadata update seeded from an asset response.
 * Modifiers return independent updates. The asset is not fetched again before sending the PUT.
 */
class AssetUpdateAtPath internal constructor(
    private val infra: RequestInfrastructure,
    private val path: String,
    private val entryId: Long,
    private val request: StoreAssetRequest,
) {
    fun withAlt(alt: String): AssetUpdateAtPath = withRequest(request.copy(alt = alt))

    fun clearAlt(): AssetUpdateAtPath = withRequest(request.copy(alt = null))

    fun withLabel(
        key: String,
        value: String,
    ): AssetUpdateAtPath = withRequest(request.copy(labels = request.labels + (key to value)))

    /** Adds [labels], replacing the values of existing keys. */
    fun withLabels(labels: Map<String, String>): AssetUpdateAtPath = withRequest(request.copy(labels = request.labels + labels))

    fun withoutLabel(key: String): AssetUpdateAtPath = withRequest(request.copy(labels = request.labels - key))

    /** Replaces all labels with a snapshot of [labels]. An empty map clears them. */
    fun replaceLabels(labels: Map<String, String>): AssetUpdateAtPath = withRequest(request.copy(labels = labels.toMap()))

    fun withTag(tag: String): AssetUpdateAtPath = withRequest(request.copy(tags = request.tags + tag))

    /** Adds [tags] to the existing tags. */
    fun withTags(tags: Set<String>): AssetUpdateAtPath = withRequest(request.copy(tags = request.tags + tags))

    fun withoutTag(tag: String): AssetUpdateAtPath = withRequest(request.copy(tags = request.tags - tag))

    /** Replaces all tags with a snapshot of [tags]. An empty set clears them. */
    fun replaceTags(tags: Set<String>): AssetUpdateAtPath = withRequest(request.copy(tags = tags.toSet()))

    /** Sends the complete editable metadata snapshot for the original entry. */
    suspend fun update(): KoniferV2Result<AssetResponse> = updateAsset(infra, path, entryId, request)

    private fun withRequest(request: StoreAssetRequest): AssetUpdateAtPath =
        AssetUpdateAtPath(
            infra = infra,
            path = path,
            entryId = entryId,
            request = request,
        )
}
