package io.direkt.s3

import io.direkt.properties.ValidatedProperties

data class S3ClientProperties(
    val endpointUrl: String?,
    val accessKey: String?,
    val secretKey: String?,
    val region: String?,
    val usePathStyleUrl: Boolean,
    val providerHint: S3Provider? = null,
) : ValidatedProperties {
    override fun validate() {
        if (providerHint == S3Provider.LOCALSTACK) {
            require(endpointUrl != null && region != null) {
                "If using localstack you must specify endpointUrl and region"
            }
        } else {
            // Reasoning for this is if endpointUrl is not set, we can assume client is using AWS S3 and a region is required
            if (endpointUrl == null && region == null) {
                throw IllegalArgumentException("Region must not be null if endpoint url is not specified")
            }
        }
    }
}
