package io.konifer.infrastructure.objectstore.s3

data class S3ClientProperties(
    val endpointUrl: String?,
    val accessKey: String?,
    val secretKey: String?,
    val region: String?,
    val forcePathStyle: Boolean = false,
    val providerHint: S3Provider? = null,
) {
    init {
        if (providerHint == S3Provider.LOCALSTACK) {
            require(endpointUrl != null && region != null) {
                "If using localstack you must specify endpointUrl and region"
            }
        }
    }
}
