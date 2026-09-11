package io.konifer.infrastructure.objectstore.s3

import io.konifer.domain.ports.FetchResult
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception

object S3ResourceReader {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun fetch(
        s3Client: S3AsyncClient,
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
}
