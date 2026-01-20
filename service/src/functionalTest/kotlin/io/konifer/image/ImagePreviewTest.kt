package io.konifer.image

import io.konifer.byteArrayToImage
import io.konifer.config.testInMemory
import io.konifer.domain.image.LQIPImplementation
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.util.createJsonClient
import io.konifer.util.fetchAssetMetadata
import io.konifer.util.fetchAssetViaRedirect
import io.konifer.util.storeAssetMultipartSource
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import org.junit.jupiter.api.Test

class ImagePreviewTest {
    companion object {
        const val PATH = "profile"
    }

    @Test
    fun `blurhash is generated and returned when storing an asset`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    image {
                        lqip = [ "blurhash" ]
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf(LQIPImplementation.BLURHASH))
        }

    @Test
    fun `thumbhash is generated and returned when storing an asset`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    image {
                        lqip = [ "thumbhash" ]
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf(LQIPImplementation.THUMBHASH))
        }

    @Test
    fun `blurhash and thumbhash are generated and returned when storing an asset`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    image {
                        lqip = [ "thumbhash", "blurhash" ]
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf(LQIPImplementation.BLURHASH, LQIPImplementation.THUMBHASH))
        }

    @Test
    fun `no lqip is generated when storing an asset if none specified`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    image {
                        lqip = [ ]
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf())
        }

    @Test
    fun `no lqip is generated when storing an asset if not enabled`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    image { }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf())
        }

    @Test
    fun `requesting a variant gives back the same LQIPs if only resizing is done`() =
        testInMemory(
            """
            path-configuration = [
                {
                    path = "/**"
                    image {
                        lqip = [ "thumbhash", "blurhash" ]
                    }
                }
            ]
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/lqip/lqip-test-1.png")!!.readBytes()
            val bufferedImage = byteArrayToImage(image)
            val request =
                StoreAssetRequest()
            storeAndAssert(client, image, request, setOf(LQIPImplementation.BLURHASH, LQIPImplementation.THUMBHASH))

            // Generate the variant
            fetchAssetViaRedirect(
                client,
                width = bufferedImage.width - 10,
                height = bufferedImage.height - 10,
                expectCacheHit = false,
            )

            fetchAssetMetadata(client, PATH)!!.apply {
                variants shouldHaveSize 2
                variants.forAll {
                    it.lqip.blurhash shouldNotBe null
                    it.lqip.thumbhash shouldNotBe null
                }
                // Assert only one unique lqip
                variants.map { it.lqip }.toSet() shouldHaveSize 1
            }
        }

    private suspend fun storeAndAssert(
        client: HttpClient,
        image: ByteArray,
        request: StoreAssetRequest,
        lqips: Set<LQIPImplementation>,
    ) {
        storeAssetMultipartSource(client, image, request, path = PATH).second!!.apply {
            variants.apply {
                size shouldBe 1
                if (lqips.contains(LQIPImplementation.BLURHASH)) {
                    first().lqip.blurhash shouldNotBe null
                } else {
                    first().lqip.blurhash shouldBe null
                }
                if (lqips.contains(LQIPImplementation.THUMBHASH)) {
                    first().lqip.thumbhash shouldNotBe null
                } else {
                    first().lqip.thumbhash shouldBe null
                }
            }
        }
        fetchAssetMetadata(client, PATH)!!.apply {
            variants.apply {
                size shouldBe 1
                if (lqips.contains(LQIPImplementation.BLURHASH)) {
                    first().lqip.blurhash shouldNotBe null
                } else {
                    first().lqip.blurhash shouldBe null
                }
                if (lqips.contains(LQIPImplementation.THUMBHASH)) {
                    first().lqip.thumbhash shouldNotBe null
                } else {
                    first().lqip.thumbhash shouldBe null
                }
            }
        }
        fetchAssetViaRedirect(client, PATH)
    }
}
