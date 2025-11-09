package io.s3

data class S3ClientProperties(
    val endpointUrl: String?,
    val accessKey: String?,
    val secretKey: String?,
    val region: String,
)
