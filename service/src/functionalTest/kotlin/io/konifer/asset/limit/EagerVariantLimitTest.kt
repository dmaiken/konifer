package io.konifer.asset.limit

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory.testImage
import io.konifer.client.fold
import io.konifer.common.http.StoreAssetRequest
import io.konifer.infrastructure.TemporaryFileFactory
import io.konifer.testInMemory
import io.konifer.util.fetchAssetInfo
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.junit.JUnitAsserter.fail
import kotlin.use

class EagerVariantLimitTest : BaseFunctionalTest() {
    @Test
    fun `eager variants cannot exceed path transformation limits`() =
        testInMemory(
            """
            variant-profiles {
              oversized {
                h = 300
              }
            }
            paths {
              "/users/**" {
                transform {
                  eager-variants = [oversized]
                  limits {
                    max-width = 14
                  }
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, attributes) = testImage()
            konifer()
                .storeAsset(
                    path = "users/123",
                    format = attributes.format,
                    request = StoreAssetRequest(),
                    bytes = image,
                ).fold(
                    onSuccess = { storeResponse ->
                        storeResponse.variants shouldHaveSize 1

                        // The eager source is deleted only after the event listener finishes handling the asset.
                        await().untilAsserted {
                            Files.walk(TemporaryFileFactory.tempDir).use { files ->
                                files
                                    .filter { Files.isRegularFile(it) }
                                    .toList()
                                    .shouldBeEmpty()
                            }
                        }

                        fetchAssetInfo(client, "users/123")!!.variants shouldHaveSize 1
                    },
                    onError = { _, _, _ -> fail("Request failed") },
                )
        }
}
