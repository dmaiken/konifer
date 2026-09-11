package io.konifer.infrastructure.asset

import io.konifer.common.http.AssetSourceRequest
import io.konifer.domain.ports.ExternalContentReference
import io.konifer.domain.ports.InvalidAssetSourceException
import software.amazon.awssdk.arns.Arn
import java.net.URI

object ExternalSourceResolver {
    fun resolve(
        source: AssetSourceRequest,
        deprecatedUrl: String?,
    ): ExternalContentReference {
        if (source.http.url == null && source.s3.arn == null && deprecatedUrl == null) {
            throw IllegalArgumentException("URL or S3 ARN must be supplied")
        }
        val url = source.http.url ?: deprecatedUrl
        if (url != null && source.s3.arn != null) {
            throw IllegalArgumentException("Only one of source.http.url or source.s3.arn, or url can be supplied")
        }

        return when {
            url != null -> {
                val uri =
                    try {
                        URI.create(url).normalize()
                    } catch (cause: Exception) {
                        throw InvalidAssetSourceException("$url is not a valid URL", cause)
                    }

                ExternalContentReference.Url(uri)
            }
            source.s3.arn != null -> {
                val (bucket, key) = parseS3ObjectArn(checkNotNull(source.s3.arn))
                ExternalContentReference.S3Object(
                    bucket = bucket,
                    key = key,
                )
            }
            else -> throw IllegalStateException("Invalid source configuration")
        }
    }

    private fun parseS3ObjectArn(arnString: String): S3ObjectLocation {
        val arn =
            try {
                Arn.fromString(arnString)
            } catch (e: IllegalArgumentException) {
                throw InvalidAssetSourceException(message = "Invalid ARN", cause = e)
            }
        if (arn.service() != "s3" || arn.region().isPresent || arn.accountId().isPresent) {
            throw InvalidAssetSourceException("Not an S3 object ARN")
        }

        val resource = arn.resourceAsString()
        val separatorIndex = resource.indexOf('/')
        if (separatorIndex <= 0 || separatorIndex == resource.lastIndex) {
            throw InvalidAssetSourceException("S3 object ARN must contain a bucket and key")
        }

        return S3ObjectLocation(
            bucket = resource.substring(0, separatorIndex),
            key = resource.substring(separatorIndex + 1),
        )
    }

    private data class S3ObjectLocation(
        val bucket: String,
        val key: String,
    )
}
