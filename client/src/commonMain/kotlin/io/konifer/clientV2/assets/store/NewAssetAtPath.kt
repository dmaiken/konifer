package io.konifer.clientV2.assets.store

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.internal.RequestInfrastructure
import io.konifer.common.http.AssetResponse

/**
 * Immutable request for storing a new asset at a selected path.
 *
 * Metadata modifiers return independent requests and leave the original request unchanged.
 */
class NewAssetAtPath internal constructor(
    private val infra: RequestInfrastructure,
    private val path: String,
    private val requestBuilder: AssetRequestBuilder,
) {
    /** Returns an independent request with [alt] as the image's alternative text. */
    fun withAlt(alt: String): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.copy(alt = alt),
        )

    /** Adds or replaces the label identified by [key]. */
    fun withLabel(
        key: String,
        value: String,
    ): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.copy(labels = requestBuilder.labels + (key to value)),
        )

    /** Adds [labels], replacing values for keys already present in the request. */
    fun withLabels(labels: Map<String, String>): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.copy(labels = requestBuilder.labels + labels),
        )

    /** Adds [tag] to the new asset. */
    fun withTag(tag: String): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.copy(tags = requestBuilder.tags + tag),
        )

    /** Adds [tags] to the new asset. */
    fun withTags(tags: Set<String>): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.copy(tags = requestBuilder.tags + tags),
        )

    /** Stores the image and metadata as a new asset entry. */
    suspend fun store(): KoniferV2Result<AssetResponse> =
        storeAsset(
            infra = infra,
            path = path,
            source = requestBuilder.assetSource,
            request = requestBuilder.build(),
        )
}
