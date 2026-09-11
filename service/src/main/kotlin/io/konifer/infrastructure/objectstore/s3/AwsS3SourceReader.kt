package io.konifer.infrastructure.objectstore.s3

import io.konifer.domain.ports.AssetSourceForbiddenException
import io.konifer.domain.ports.AssetSourceTimeoutException
import io.konifer.domain.ports.AssetSourceUnavailableException
import io.konifer.domain.ports.FetchResult
import io.konifer.domain.ports.InvalidAssetSourceException
import io.ktor.utils.io.ByteWriteChannel
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException
import software.amazon.awssdk.core.exception.ApiCallTimeoutException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.S3Exception

class AwsS3SourceReader(
    private val s3Client: Lazy<S3AsyncClient>,
) {
    suspend fun fetch(
        bucket: String,
        key: String,
        channel: ByteWriteChannel,
    ): FetchResult =
        try {
            S3ResourceReader.fetch(
                s3Client = s3Client.value,
                bucket = bucket,
                key = key,
                channel = channel,
            )
        } catch (cause: ApiCallTimeoutException) {
            throw AssetSourceTimeoutException(cause)
        } catch (cause: ApiCallAttemptTimeoutException) {
            throw AssetSourceTimeoutException(cause)
        } catch (cause: S3Exception) {
            when (cause.statusCode()) {
                401, 403 ->
                    throw AssetSourceForbiddenException(
                        "Amazon S3 denied access to the asset source",
                    )

                in 400..499 ->
                    throw InvalidAssetSourceException(
                        "Amazon S3 rejected the asset source",
                        cause,
                    )

                else ->
                    throw AssetSourceUnavailableException(
                        "Amazon S3 could not retrieve the asset source",
                        cause,
                    )
            }
        } catch (cause: SdkClientException) {
            // Includes missing credentials, signing failures, DNS and connection failures.
            throw AssetSourceUnavailableException(
                "Amazon S3 source is unavailable",
                cause,
            )
        }
}
