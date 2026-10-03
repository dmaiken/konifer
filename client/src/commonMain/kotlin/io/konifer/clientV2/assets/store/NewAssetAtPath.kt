package io.konifer.clientV2.assets.store

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.internal.RequestInfrastructure
import io.konifer.common.http.AssetResponse

/** Metadata modifiers return independent requests, leaving the original unchanged. */
class NewAssetAtPath internal constructor(
    private val infra: RequestInfrastructure,
    private val path: String,
    private val requestBuilder: AssetRequestBuilder,
) {
    fun withAlt(alt: String): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.copy(alt = alt),
        )

    fun withLabel(
        key: String,
        value: String,
    ): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.copy(labels = requestBuilder.labels + (key to value)),
        )

    fun withLabels(labels: Map<String, String>): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.copy(labels = requestBuilder.labels + labels),
        )

    fun withTag(tag: String): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.copy(tags = requestBuilder.tags + tag),
        )

    fun withTags(tags: Set<String>): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.copy(tags = requestBuilder.tags + tags),
        )

    suspend fun store(): KoniferV2Result<AssetResponse> =
        storeAsset(
            infra = infra,
            path = path,
            source = requestBuilder.assetSource,
            request = requestBuilder.build(),
        )
}
