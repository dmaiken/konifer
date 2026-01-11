package io.direkt.infrastructure.objectstore.s3

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

data class S3ClientProperties(
    val endpointUrl: String?,
    val accessKey: String?,
    val secretKey: String?,
    val region: String?,
    val usePathStyleUrl: Boolean,
    val presignedUrlProperties: PresignedUrlProperties?,
    val providerHint: S3Provider? = null,
) {
    init {
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

    val endpointDomain = endpointUrl?.replaceFirst("https://", "")?.replaceFirst("http://", "")
}

data class PresignedUrlProperties(
    val ttl: Duration,
) {
    init {
        require(ttl.isPositive()) {
            "Presigned TTL must be positive"
        }
        require(ttl <= 7.days) {
            "Presigned TTL cannot be greater than 7 days"
        }
    }
}
