package io.direkt.asset

import io.direkt.config.testInMemory
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAllAssetMetadata
import io.direkt.util.storeAssetMultipartSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Test

class FetchAssetMetadataLimitTest {
    @Test
    fun `limit is respected when fetching asset`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val labels =
                mapOf(
                    "phone" to "iphone",
                    "type" to "vegetable",
                )
            val tags = setOf("smart", "cool")
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    labels = labels,
                    tags = tags,
                )
            coroutineScope {
                buildList {
                    repeat(10) {
                        add(
                            async {
                                storeAssetMultipartSource(client, image, request, path = "profile").second.apply {
                                    this shouldNotBe null
                                }
                            },
                        )
                    }
                }.awaitAll()
            }

            fetchAllAssetMetadata(
                client = client,
                path = "profile",
                limit = 5,
            ) shouldHaveSize 5

            fetchAllAssetMetadata(
                client = client,
                path = "profile",
                limit = 50,
            ) shouldHaveSize 10
        }

    @Test
    fun `can have limit greater than amount at path`() =
        testInMemory {
            val client = createJsonClient()
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val labels =
                mapOf(
                    "phone" to "iphone",
                    "type" to "vegetable",
                )
            val tags = setOf("smart", "cool")
            val request =
                StoreAssetRequest(
                    alt = "an image",
                    labels = labels,
                    tags = tags,
                )
            coroutineScope {
                buildList {
                    repeat(10) {
                        add(
                            async {
                                storeAssetMultipartSource(client, image, request, path = "profile").second.apply {
                                    this shouldNotBe null
                                }
                            },
                        )
                    }
                }.awaitAll()
            }

            fetchAllAssetMetadata(
                client = client,
                path = "profile",
                limit = 50,
            ) shouldHaveSize 10
        }
}
