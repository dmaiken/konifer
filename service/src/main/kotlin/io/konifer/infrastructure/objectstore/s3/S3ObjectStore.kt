package io.konifer.infrastructure.objectstore.s3

import io.konifer.domain.path.RedirectProperties
import io.konifer.domain.path.RedirectStrategy
import io.konifer.domain.ports.FetchResult
import io.konifer.domain.ports.ObjectStore
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import java.io.File
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class S3ObjectStore(
    private val s3Client: S3AsyncClient,
    private val s3TransferManager: S3TransferManager,
    private val s3Presigner: S3Presigner,
) : ObjectStore {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override suspend fun persist(
        bucket: String,
        key: String,
        file: File,
    ): LocalDateTime =
        withContext(Dispatchers.IO) {
            val uploadFileRequest =
                UploadFileRequest
                    .builder()
                    .putObjectRequest { b: PutObjectRequest.Builder -> b.bucket(bucket).key(key) }
                    .source(file.toPath())
                    .build()
            s3TransferManager.uploadFile(uploadFileRequest).completionFuture().await()

            LocalDateTime.now()
        }

    override suspend fun fetch(
        bucket: String,
        key: String,
        channel: ByteWriteChannel,
    ): FetchResult =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    GetObjectRequest
                        .builder()
                        .bucket(bucket)
                        .key(key)
                        .build()

                val responsePublisher =
                    s3Client
                        .getObject(request, AsyncResponseTransformer.toPublisher())
                        .await()

                responsePublisher.asFlow().collect { byteBuffer ->
                    channel.writeFully(byteBuffer)
                }
                channel.flushAndClose()

                FetchResult.found(responsePublisher.response().contentLength())
            } catch (e: NoSuchKeyException) {
                logger.info("Object with key: $key in bucket: $bucket does not exist", e)
                channel.flushAndClose()
                FetchResult.NOT_FOUND
            } catch (e: S3Exception) {
                channel.flushAndClose()
                // In case providers throw this
                if (e.statusCode() == 404) {
                    logger.info("Object with key: $key in bucket: $bucket does not exist", e)
                    FetchResult.NOT_FOUND
                } else {
                    logger.warn("Threw exception when fetching", e)
                    throw e
                }
            } catch (e: Exception) {
                logger.warn("Threw exception when fetching", e)
                channel.flushAndClose()
                throw e
            }
        }

    override suspend fun exists(
        bucket: String,
        key: String,
    ): Boolean =
        try {
            s3Client
                .headObject(
                    HeadObjectRequest
                        .builder()
                        .bucket(bucket)
                        .key(key)
                        .build(),
                ).await()

            true
        } catch (e: NoSuchKeyException) {
            logger.info("Object with key: $key in bucket: $bucket does not exist", e)
            false
        } catch (e: S3Exception) {
            // In case providers throw this
            if (e.statusCode() == 404) {
                false
            } else {
                throw e
            }
        }

    override suspend fun delete(
        bucket: String,
        key: String,
    ) {
        try {
            s3Client
                .deleteObject(
                    DeleteObjectRequest
                        .builder()
                        .bucket(bucket)
                        .key(key)
                        .build(),
                ).await()
        } catch (e: Exception) {
            logger.error("Unable to delete asset with key: $key from bucket: $bucket", e)
            throw e
        }
    }

    override suspend fun deleteAll(
        bucket: String,
        keys: List<String>,
    ) {
        // max 1000 keys allowed per S3 request
        keys.chunked(1000).forEach { chunk ->
            val objectsToDelete =
                chunk.map { key ->
                    ObjectIdentifier.builder().key(key).build()
                }

            val deletePayload =
                Delete
                    .builder()
                    .objects(objectsToDelete)
                    .quiet(true) // Don't return success information
                    .build()

            val request =
                DeleteObjectsRequest
                    .builder()
                    .bucket(bucket)
                    .delete(deletePayload)
                    .build()

            s3Client.deleteObjects(request).await()
        }
    }

    override suspend fun generateObjectUrl(
        bucket: String,
        key: String,
        properties: RedirectProperties,
    ): String? =
        when (properties.strategy) {
            RedirectStrategy.PRESIGNED ->
                presignUrl(
                    bucket = bucket,
                    key = key,
                    ttl = properties.preSigned.ttl,
                )
            RedirectStrategy.TEMPLATE ->
                properties.template.resolve(
                    bucket = bucket,
                    key = key,
                )
            RedirectStrategy.NONE -> null
        }

    private suspend fun presignUrl(
        bucket: String,
        key: String,
        ttl: Duration,
    ): String =
        withContext(Dispatchers.IO) {
            val getObjectRequest =
                GetObjectRequest
                    .builder()
                    .bucket(bucket)
                    .key(key)
                    .build()

            val presignRequest =
                GetObjectPresignRequest
                    .builder()
                    .signatureDuration(ttl.toJavaDuration())
                    .getObjectRequest(getObjectRequest)
                    .build()

            s3Presigner.presignGetObject(presignRequest).url().toString()
        }
}
