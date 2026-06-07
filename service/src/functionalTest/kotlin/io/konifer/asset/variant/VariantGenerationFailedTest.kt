package io.konifer.asset.variant

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.client.EntryId
import io.konifer.client.requestedTransformation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.infrastructure.vips.VipsImageProcessor
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import io.kotest.matchers.collections.shouldHaveSize
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.koin.dsl.module

class VariantGenerationFailedTest : BaseFunctionalTest() {
    @Test
    fun `failure during preprocessing results in server error`() {
        val imageProcessor =
            mockk<VipsImageProcessor> {
                coEvery {
                    preprocess(
                        source = any(),
                        sourceFormat = any(),
                        transformationDataContainer = any(),
                        lqipImplementations = any(),
                    )
                } throws RuntimeException("boom")
            }

        testInMemory(
            configuration =
                """
                paths {
                  "/**" {
                    transform {
                      preprocessing {
                        enabled = true
                        w = 100
                      }
                    }
                  }
                }
                """.trimIndent(),
            modules =
                listOf(
                    module {
                        single<VipsImageProcessor> { imageProcessor }
                    },
                ),
        ) {
            val (image, attributes) = ImageFactory.testImage()

            konifer.storeAsset(
                path = "profile",
                format = attributes.format,
                request = StoreAssetRequest(),
                bytes = image,
            ) shouldHaveHttpError HttpStatusCode.InternalServerError.value

            konifer
                .fetchAssetMetadata(
                    path = "profile",
                    limit = 10,
                ).shouldBeSuccessful()
                .body shouldHaveSize 0
        }
    }

    @Test
    fun `failure when generating on demand variant returns server error`() {
        val imageProcessor =
            mockk<VipsImageProcessor> {
                coEvery {
                    generateVariants(
                        source = any(),
                        transformationDataContainers = any(),
                        lqipImplementations = any(),
                    )
                } throws RuntimeException("boom")
            }

        testInMemory(
            modules =
                listOf(
                    module {
                        single<VipsImageProcessor> { imageProcessor }
                    },
                ),
        ) {
            val (image, attributes) = ImageFactory.testImage()

            val asset =
                konifer
                    .storeAsset(
                        path = "profile",
                        format = attributes.format,
                        request = StoreAssetRequest(),
                        bytes = image,
                    ).shouldBeSuccessful()
                    .body

            konifer.fetchAssetContentBytes(
                path = "profile",
                requestedTransformation =
                    requestedTransformation {
                        width = 100
                    },
            ) shouldHaveHttpError HttpStatusCode.InternalServerError.value

            konifer
                .fetchAssetMetadata(
                    path = "profile",
                    querySelectors = EntryId(asset.entryId),
                ).shouldBeSuccessful()
                .body.variants shouldHaveSize 1 // Original variant only
        }
    }
}
