package io.konifer.client.assets.update

import io.konifer.client.KoniferResult
import io.konifer.client.internal.RequestInfrastructure
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
    /** Returns an independent update with [alt] as the image's alternative text. */
    fun withAlt(alt: String): AssetUpdateAtPath = withRequest(request.copy(alt = alt))

    /** Returns an independent update that clears the image's alternative text. */
    fun clearAlt(): AssetUpdateAtPath = withRequest(request.copy(alt = null))

    /** Adds or replaces the label identified by [key]. */
    fun withLabel(
        key: String,
        value: String,
    ): AssetUpdateAtPath = withRequest(request.copy(labels = request.labels + (key to value)))

    /** Adds [labels], replacing the values of existing keys. */
    fun withLabels(labels: Map<String, String>): AssetUpdateAtPath = withRequest(request.copy(labels = request.labels + labels))

    /** Removes the label identified by [key]. */
    fun withoutLabel(key: String): AssetUpdateAtPath = withRequest(request.copy(labels = request.labels - key))

    /** Replaces all labels with a snapshot of [labels]. An empty map clears them. */
    fun replaceLabels(labels: Map<String, String>): AssetUpdateAtPath = withRequest(request.copy(labels = labels.toMap()))

    /** Adds [tag] to the existing tags. */
    fun withTag(tag: String): AssetUpdateAtPath = withRequest(request.copy(tags = request.tags + tag))

    /** Adds [tags] to the existing tags. */
    fun withTags(tags: Set<String>): AssetUpdateAtPath = withRequest(request.copy(tags = request.tags + tags))

    /** Removes [tag] from the existing tags. */
    fun withoutTag(tag: String): AssetUpdateAtPath = withRequest(request.copy(tags = request.tags - tag))

    /** Replaces all tags with a snapshot of [tags]. An empty set clears them. */
    fun replaceTags(tags: Set<String>): AssetUpdateAtPath = withRequest(request.copy(tags = tags.toSet()))

    /** Sends the complete editable metadata snapshot for the original entry. */
    suspend fun update(): KoniferResult<AssetResponse> = updateAsset(infra, path, entryId, request)

    private fun withRequest(request: StoreAssetRequest): AssetUpdateAtPath =
        AssetUpdateAtPath(
            infra = infra,
            path = path,
            entryId = entryId,
            request = request,
        )
}
