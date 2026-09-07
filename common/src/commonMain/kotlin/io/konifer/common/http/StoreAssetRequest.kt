package io.konifer.common.http

import kotlinx.serialization.Serializable

@Serializable
data class StoreAssetRequest(
    val alt: String? = null,
    val url: String? = null,
    val source: AssetSource? = null,
    val labels: Map<String, String> = emptyMap(),
    val tags: Set<String> = emptySet(),
)

@Serializable
data class AssetSource(
    val http: HttpSource = HttpSource(),
    val s3: S3Source = S3Source(),
)

@Serializable
data class HttpSource(
    val url: String? = null,
)

@Serializable
data class S3Source(
    val arn: String? = null,
)
