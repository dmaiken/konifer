package io.konifer.asset.store

import io.konifer.BaseLocalstackTestContainersTest
import io.konifer.ImageFactory
import io.konifer.common.asset.AssetSource
import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.HttpSource
import io.konifer.common.http.S3Source
import io.konifer.common.http.StoreAssetRequest
import io.konifer.infrastructure.objectstore.s3.AwsS3SourceReader
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

class StoreAssetExternalArnTest : BaseLocalstackTestContainersTest() {
    @Test
    fun `can upload an asset by supplying an arn`() {
        val bucket = "external-assets"
        val key = "images/joshua-tree.jpeg"
        val arn = "arn:aws:s3:::$bucket/$key"
        val (image, attributes) = ImageFactory.testImage()

        createLocalstackS3Client().use { s3Client ->
            s3Client
                .createBucket(CreateBucketRequest.builder().bucket(bucket).build())
                .join()
            s3Client
                .putObject(
                    PutObjectRequest
                        .builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(attributes.format.mimeType)
                        .build(),
                    AsyncRequestBody.fromBytes(image),
                ).join()

            testInMemory(
                modules =
                    listOf(
                        module {
                            single<AwsS3SourceReader> {
                                AwsS3SourceReader(lazy { s3Client })
                            }
                        },
                    ),
            ) {
                val path = "external-arn"
                val storedAsset =
                    konifer()
                        .storeAsset(
                            path = path,
                            request =
                                StoreAssetRequest(
                                    alt = "asset supplied by ARN",
                                    source =
                                        AssetSourceRequest(
                                            s3 = S3Source(arn = arn),
                                        ),
                                ),
                        ).shouldBeSuccessful()
                        .body

                storedAsset.source shouldBe AssetSource.ARN
                storedAsset.sourceUrl shouldBe arn
                storedAsset.externalSourceAddress shouldBe arn
                storedAsset.alt shouldBe "asset supplied by ARN"
                storedAsset.variants
                    .single()
                    .attributes.format shouldBe attributes.format.format

                val storedContent =
                    konifer()
                        .fetchAssetContentBytes(path = path)
                        .shouldBeSuccessful()
                        .body
                storedContent.contentEquals(image) shouldBe true
            }
        }
    }

    @Test
    fun `if no s3 provider chain exists then arn upload returns bad gateway`() =
        testInMemory {
            val path = "external-arn"
            val response =
                konifer()
                    .storeAsset(
                        path = path,
                        request =
                            StoreAssetRequest(
                                alt = "asset supplied by ARN",
                                source =
                                    AssetSourceRequest(
                                        s3 = S3Source(arn = "arn:aws:s3:::bucket/key"),
                                    ),
                            ),
                    ) shouldHaveHttpError HttpStatusCode.BadGateway.value

            response.message shouldBe "Amazon S3 source client could not be initialized"
        }

    @Test
    fun `arn not found returns unprocessable entity`() {
        val bucket = "external-assets"
        val key = "images/joshua-tree.png"
        val arn = "arn:aws:s3:::$bucket/$key"
        val (image, attributes) = ImageFactory.testImage()

        createLocalstackS3Client().use { s3Client ->
            s3Client
                .createBucket(CreateBucketRequest.builder().bucket(bucket).build())
                .join()
            s3Client
                .putObject(
                    PutObjectRequest
                        .builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(attributes.format.mimeType)
                        .build(),
                    AsyncRequestBody.fromBytes(image),
                ).join()

            testInMemory(
                modules =
                    listOf(
                        module {
                            single<AwsS3SourceReader> {
                                AwsS3SourceReader(lazy { s3Client })
                            }
                        },
                    ),
            ) {
                val path = "external-arn"
                val response =
                    konifer()
                        .storeAsset(
                            path = path,
                            request =
                                StoreAssetRequest(
                                    alt = "asset supplied by ARN",
                                    source =
                                        AssetSourceRequest(
                                            s3 = S3Source(arn = arn + "DoesNotExist"),
                                        ),
                                ),
                        ) shouldHaveHttpError HttpStatusCode.UnprocessableEntity.value

                response.message shouldBe "S3 object not found"

                konifer().fetchAssetContentBytes(path = path) shouldHaveHttpError HttpStatusCode.NotFound.value
            }
        }
    }

    @Test
    fun `supplying both arn and url returns a bad request`() =
        testInMemory(
            """
            source = {
              url = {
                allowed-domains = [ konifer.io ]
              }
            }
            """.trimIndent(),
        ) {
            val path = "external-arn"
            val response =
                konifer()
                    .storeAsset(
                        path = path,
                        request =
                            StoreAssetRequest(
                                alt = "asset supplied by ARN",
                                source =
                                    AssetSourceRequest(
                                        s3 = S3Source(arn = "arn:aws:s3:::bucket/key"),
                                        http = HttpSource(url = "https://konifer.io/img/konifer-small.png"),
                                    ),
                            ),
                    ) shouldHaveHttpError HttpStatusCode.BadRequest.value

            response.message shouldBe "Only one of source.http.url or source.s3.arn, or url can be supplied"
        }
}
