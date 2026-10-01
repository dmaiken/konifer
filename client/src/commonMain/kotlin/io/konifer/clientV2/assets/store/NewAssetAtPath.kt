package io.konifer.clientV2.assets.store

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.KoniferV2Result
import io.konifer.common.http.AssetResponse

class NewAssetAtPath internal constructor(
    private val infra: RequestInfrastructure,
    private val path: String,
    private val requestBuilder: AssetRequestBuilder,
) {
    fun withAlt(alt: String): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.apply { this.alt = alt },
        )

    fun withLabel(
        key: String,
        value: String,
    ): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.apply { this.labels[key] = value },
        )

    fun withLabels(labels: Map<String, String>): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.apply { this.labels.putAll(labels) },
        )

    fun withTag(tag: String): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.apply { this.tags.add(tag) },
        )

    fun withTags(tags: Set<String>): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder = requestBuilder.apply { this.tags.addAll(tags) },
        )

    suspend fun store(): KoniferV2Result<AssetResponse> =
        storeAsset(
            infra = infra,
            path = path,
            source = requestBuilder.assetSource,
            request = requestBuilder.build(),
        )
}
