package io.konifer.infrastructure.objectstore.s3

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
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.content.writeToOutputStream
import io.konifer.domain.ports.FetchResult
import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.objectstore.property.ObjectStoreProperties
import io.konifer.infrastructure.objectstore.property.RedirectStrategy
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

class S3ObjectStore(
    private val s3Client: S3Client,
) : ObjectStore {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override suspend fun persist(
        bucket: String,
        key: String,
        file: File,
    ): LocalDateTime =
        withContext(Dispatchers.IO) {
            s3Client.putObject(
                input =
                    PutObjectRequest {
                        this.bucket = bucket
                        this.key = key
                        body = ByteStream.fromFile(file)
                    },
            )

            LocalDateTime.now()
        }

    override suspend fun fetch(
        bucket: String,
        key: String,
        channel: ByteWriteChannel,
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
                        body.writeToOutputStream(channel.toOutputStream())
                        FetchResult.found(requireNotNull(it.contentLength))
                    } ?: FetchResult.NOT_FOUND.also {
                        channel.flushAndClose()
                    }
                }
            } catch (e: NoSuchKey) {
                logger.info("Object with key: $key in bucket: $bucket does not exist", e)
                FetchResult.NOT_FOUND.also {
                    channel.flushAndClose()
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

    override suspend fun generateObjectUrl(
        bucket: String,
        key: String,
        properties: ObjectStoreProperties,
    ): String? =
        when (properties.redirect.strategy) {
            RedirectStrategy.PRESIGNED ->
                withContext(Dispatchers.IO) {
                    s3Client.presignGetObject(
                        input =
                            GetObjectRequest {
                                this.bucket = bucket
                                this.key = key
                            },
                        duration = properties.redirect.preSigned.ttl,
                    )
                }.url.toString()
            RedirectStrategy.TEMPLATE ->
                properties.redirect.template.resolve(
                    bucket = bucket,
                    key = key,
                )
            RedirectStrategy.NONE -> null
        }
}
