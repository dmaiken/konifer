package io.konifer.asset.fetch

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory.testImage
import io.konifer.TestImageType
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.ports.AssetRepository
import io.konifer.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.konifer.infrastructure.http.APP_CACHE_STATUS
import io.konifer.testInMemory
import io.konifer.util.storeAssetMultipartSource
import io.konifer.util.updateAsset
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class FetchAssetResolutionTest : BaseFunctionalTest() {
    private lateinit var repository: ObservedAssetRepository
    private val observedModule =
        module {
            single<AssetRepository> {
                ObservedAssetRepository(get<InMemoryAssetRepository>()).also { repository = it }
            }
        }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `competing generation reads only the winning variant`(winnerUploaded: Boolean) =
        testInMemory(modules = listOf(observedModule)) {
            storeAssetMultipartSource(client, testImage().bytes, StoreAssetRequest())
            repository.resetReads()
            val reads = AtomicInteger()
            val inserts = AtomicInteger()
            val bothRead = CompletableDeferred<Unit>()
            val inserted = CompletableDeferred<Unit>()
            val uploaded = CompletableDeferred<Unit>()
            val loserFinished = CompletableDeferred<Unit>()
            repository.afterAssetRead = {
                if (reads.incrementAndGet() == 2) bothRead.complete(Unit)
                bothRead.await()
            }
            repository.beforeVariantInsert = {
                if (inserts.incrementAndGet() == 2) {
                    if (winnerUploaded) uploaded.await() else inserted.await()
                }
            }
            repository.afterVariantInsert = {
                inserted.complete(Unit)
                if (!winnerUploaded) loserFinished.await()
            }
            repository.afterVariantUpload = { uploaded.complete(Unit) }

            val responses =
                withTimeout(30_000) {
                    List(2) {
                        async {
                            client.get("/assets/profile/-/link?width=100").also {
                                if (it.status == HttpStatusCode.NotFound) loserFinished.complete(Unit)
                            }
                        }
                    }.awaitAll()
                }
            responses.count { it.status == HttpStatusCode.OK } shouldBe if (winnerUploaded) 2 else 1
            responses.count { it.status == HttpStatusCode.NotFound } shouldBe if (winnerUploaded) 0 else 1
            responses.filter { it.status == HttpStatusCode.OK }.forEach { it.headers[APP_CACHE_STATUS] shouldBe "miss" }
            repository.assetReads.get() shouldBe 2
            repository.variantReads.get() shouldBe 1
        }

    @ParameterizedTest
    @EnumSource(ReturnFormat::class, names = ["LINK", "REDIRECT", "CONTENT", "DOWNLOAD"])
    fun `delivery loads once for originals generated variants and cached variants`(returnFormat: ReturnFormat) =
        testInMemory(modules = listOf(observedModule)) {
            configureClient { followRedirects = false }
            val image = testImage().bytes
            storeAssetMultipartSource(client, image, StoreAssetRequest())
            val path = "/assets/profile/-/${returnFormat.name.lowercase()}"
            val expectedStatus = if (returnFormat == ReturnFormat.REDIRECT) HttpStatusCode.TemporaryRedirect else HttpStatusCode.OK

            repository.resetReads()
            client.get(path).apply {
                status shouldBe expectedStatus
                headers[APP_CACHE_STATUS] shouldBe "hit"
                bodyAsBytes()
            }
            repository.assetReads.get() shouldBe 1
            repository.variantReads.get() shouldBe 0
            repository.lastAsset!!.variants shouldHaveSize 1

            repository.resetReads()
            client.get("$path?width=100").apply {
                status shouldBe expectedStatus
                headers[APP_CACHE_STATUS] shouldBe "miss"
                bodyAsBytes()
            }
            repository.assetReads.get() shouldBe 1
            repository.variantReads.get() shouldBe 0
            repository.uploadedVariant!!
                .attributes.width.value shouldBe 100

            repository.resetReads()
            client.get("$path?width=100").apply {
                status shouldBe expectedStatus
                headers[APP_CACHE_STATUS] shouldBe "hit"
                bodyAsBytes()
            }
            repository.assetReads.get() shouldBe 1
            repository.variantReads.get() shouldBe 0
            repository.lastAsset!!.variants shouldHaveSize 2
        }

    @Test
    fun `metadata returns all variants without extra reads`() =
        testInMemory(modules = listOf(observedModule)) {
            storeAssetMultipartSource(client, testImage().bytes, StoreAssetRequest())
            client.get("/assets/profile/-/link?width=100").status shouldBe HttpStatusCode.OK

            repository.resetReads()
            client.get("/assets/profile/-/info").body<AssetResponse>().variants shouldHaveSize 2
            repository.assetReads.get() shouldBe 1
            repository.variantReads.get() shouldBe 0

            repository.resetReads()
            client
                .get("/assets/profile/-/entries")
                .body<AssetEntriesResponse>()
                .entries
                .single()
                .variants shouldHaveSize 2
            repository.collectionReads.get() shouldBe 1
            repository.assetReads.get() shouldBe 0
            repository.variantReads.get() shouldBe 0
        }

    @Test
    fun `transformation equivalent to original is a cache hit`() =
        testInMemory(modules = listOf(observedModule)) {
            storeAssetMultipartSource(client, testImage(ImageFormat.PNG).bytes, StoreAssetRequest())
            repository.resetReads()

            client.get("/assets/profile/-/link?format=png").apply {
                status shouldBe HttpStatusCode.OK
                headers[APP_CACHE_STATUS] shouldBe "hit"
            }
            repository.assetReads.get() shouldBe 1
            repository.variantReads.get() shouldBe 0
            repository.uploadedVariant shouldBe null
        }

    @ParameterizedTest
    @EnumSource(ReturnFormat::class, names = ["LINK", "REDIRECT", "CONTENT", "DOWNLOAD"])
    fun `missing transformed asset returns not found after one read`(returnFormat: ReturnFormat) =
        testInMemory(modules = listOf(observedModule)) {
            client.get("/assets/missing/-/${returnFormat.name.lowercase()}?width=100").status shouldBe HttpStatusCode.NotFound
            repository.assetReads.get() shouldBe 1
            repository.variantReads.get() shouldBe 0
        }

    @Test
    fun `normalization uses original selected by labels and modified order`() =
        testInMemory(modules = listOf(observedModule)) {
            val first =
                storeAssetMultipartSource(
                    client,
                    testImage(ImageFormat.PNG, TestImageType.MOON).bytes,
                    StoreAssetRequest(labels = mapOf("kind" to "moon")),
                )
            storeAssetMultipartSource(client, testImage(ImageFormat.PNG).bytes, StoreAssetRequest(labels = mapOf("kind" to "tree")))
            val original =
                first.second!!
                    .variants
                    .single()
                    .attributes
            updateAsset(client, first.first[HttpHeaders.Location]!!, StoreAssetRequest(alt = "updated", labels = mapOf("kind" to "moon")))

            listOf("link?width=100&kind=moon", "modified/link?width=100").forEach { selection ->
                repository.resetReads()
                val response = client.get("/assets/profile/-/$selection")
                response.status shouldBe HttpStatusCode.OK
                val link = response.body<AssetLinkResponse>()
                link.attributes.height shouldBe (original.height * 100.0 / original.width).roundToInt()
                repository.lastAsset!!.entryId shouldBe first.second!!.entryId
                repository.assetReads.get() shouldBe 1
                repository.variantReads.get() shouldBe 0
            }
        }

    @Test
    fun `normalized dimensions are validated before generation`() =
        testInMemory(
            configuration = """paths { "/**" { transform { limits { max-height = 1 } } } }""",
            modules = listOf(observedModule),
        ) {
            storeAssetMultipartSource(client, testImage().bytes, StoreAssetRequest())
            repository.resetReads()
            client.get("/assets/profile/-/link?width=100").status shouldBe HttpStatusCode.BadRequest
            repository.assetReads.get() shouldBe 1
            repository.uploadedVariant shouldBe null
        }
}
