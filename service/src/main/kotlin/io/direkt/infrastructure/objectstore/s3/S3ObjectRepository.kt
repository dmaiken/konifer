package io.direkt.infrastructure.objectstore.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectsRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.content.writeToOutputStream
import io.direkt.asset.model.AssetVariant
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.ports.FetchResult
import io.direkt.domain.ports.ObjectRepository
import io.direkt.domain.ports.PersistResult
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

class S3ObjectRepository(
    private val s3Client: S3Client,
    private val s3ClientProperties: S3ClientProperties,
) : ObjectRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override suspend fun persist(
        bucket: String,
        key: String,
        asset: File,
    ): LocalDateTime =
        withContext(Dispatchers.IO) {
            s3Client.putObject(
                input =
                    PutObjectRequest {
                        this.bucket = bucket
                        this.key = key
                        body = ByteStream.fromFile(asset)
                    },
            )

            LocalDateTime.now()
        }

    override suspend fun fetch(
        bucket: String,
        key: String,
        stream: ByteWriteChannel,
    ): FetchResult =
        withContext(Dispatchers.IO) {
            try {
                s3Client.getObject(
                    input =
                        GetObjectRequest {
                            this.bucket = bucket
                            this.key = key
                        },
                ) {
                    it.body?.let { body ->
                        body.writeToOutputStream(stream.toOutputStream())
                        FetchResult.found(requireNotNull(it.contentLength))
                    } ?: FetchResult.NOT_FOUND.also {
                        stream.flushAndClose()
                    }
                }
            } catch (e: NoSuchKey) {
                logger.info("Object with key $key in bucket $bucket does not exist", e)
                FetchResult.NOT_FOUND.also {
                    stream.flushAndClose()
                }
            }
        }

    override suspend fun exists(
        bucket: String,
        key: String,
    ): Boolean =
        try {
            s3Client.headObject(
                HeadObjectRequest {
                    this.bucket = bucket
                    this.key = key
                },
            )
            true
        } catch (e: NotFound) {
            logger.info("Object with key $key in bucket $bucket does not exist", e)
            false
        }

    override suspend fun delete(
        bucket: String,
        key: String,
    ) {
        try {
            s3Client.deleteObject(
                input =
                    DeleteObjectRequest {
                        this.bucket = bucket
                        this.key = key
                    },
            )
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
            s3Client.deleteObjects(
                input =
                    DeleteObjectsRequest {
                        this.bucket = bucket
                        delete =
                            Delete {
                                objects =
                                    chunk.map {
                                        ObjectIdentifier { key = it }
                                    }
                            }
                    },
            )
        }
    }

    override fun generateObjectUrl(variant: AssetVariant): String {
        return if (s3ClientProperties.usePathStyleUrl) {
            s3ClientProperties.endpointUrl?.let { endpointUrl ->
                // Not using AWS S3
                "https://$endpointUrl/${variant.objectStoreBucket}/${variant.objectStoreKey}"
            } ?: "https://s3.${s3ClientProperties.region}.amazonaws.com/${variant.objectStoreBucket}/${variant.objectStoreKey}"
        } else {
            return s3ClientProperties.endpointUrl?.let { endpointUrl ->
                // Not using AWS S3
                "https://${variant.objectStoreBucket}.$endpointUrl/${variant.objectStoreKey}"
            } ?: "https://${variant.objectStoreBucket}.s3.${s3ClientProperties.region}.amazonaws.com/${variant.objectStoreKey}"
        }
    }
}
