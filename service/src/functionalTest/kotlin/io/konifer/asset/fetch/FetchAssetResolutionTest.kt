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
import io.konifer.domain.ports.EventPublisher
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.VariantSelection
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.retention.CacheProperties
import io.konifer.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.konifer.infrastructure.event.InMemoryEventBus
import io.konifer.infrastructure.http.APP_CACHE_STATUS
import io.konifer.testInMemory
import io.konifer.util.storeAssetMultipartSource
import io.konifer.util.updateAsset
import io.kotest.matchers.collections.shouldContainExactly
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
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class FetchAssetResolutionTest : BaseFunctionalTest() {
    private lateinit var repository: ObservedAssetRepository
    private lateinit var persistedRepository: InMemoryAssetRepository
    private lateinit var eventPublisher: ObservedEventPublisher
    private val observedModule =
        module {
            single<AssetRepository> {
                persistedRepository = get<InMemoryAssetRepository>()
                ObservedAssetRepository(persistedRepository).also { repository = it }
            }
            single<EventPublisher> {
                ObservedEventPublisher(get<InMemoryEventBus>()).also { eventPublisher = it }
            }
        }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `competing generation refreshes variants and selects the uploaded winner`(winnerUploaded: Boolean) =
        testInMemory(modules = listOf(observedModule)) {
            storeAssetMultipartSource(client, testImage().bytes, StoreAssetRequest())
            resetObservations()
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
            repository.afterVariantUpload = {
                val winner = checkNotNull(repository.uploadedVariant)
                val transformation = (winner.specification as VariantSpecification.Transformed).transformation
                val unrelated =
                    persistedRepository.storeNewVariant(
                        Variant.Pending.newVariant(
                            assetId = winner.assetId,
                            attributes = winner.attributes,
                            transformation = transformation.copy(width = 50.toDimension()),
                            objectStoreBucket = winner.objectStoreBucket,
                            objectStoreKey = "unrelated.png",
                            lqip = winner.lqips,
                            expiresAt = null,
                        ),
                    )
                persistedRepository.markUploaded(unrelated.markReady(Instant.now()), CacheProperties())
                uploaded.complete(Unit)
            }

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
            assertDeliveryReads(expected = 2)
            repository.variantReads.get() shouldBe 1
            eventPublisher.accesses shouldHaveSize responses.count { it.status == HttpStatusCode.OK }
            eventPublisher.accesses.map { it.variantId }.toSet() shouldBe setOf(repository.uploadedVariant!!.id)
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

            resetObservations()
            client.get(path).apply {
                status shouldBe expectedStatus
                headers[APP_CACHE_STATUS] shouldBe "hit"
                bodyAsBytes()
            }
            assertDeliveryReads()
            repository.lastDeliverySelection shouldBe VariantSelection.ORIGINAL_ONLY
            repository.variantReads.get() shouldBe 0
            repository.lastDeliveryAsset!!.variants shouldHaveSize 1
            assertAccess(
                repository.lastDeliveryAsset!!
                    .variants
                    .single()
                    .id,
            )

            resetObservations()
            client.get("$path?width=100").apply {
                status shouldBe expectedStatus
                headers[APP_CACHE_STATUS] shouldBe "miss"
                bodyAsBytes()
            }
            assertDeliveryReads()
            repository.lastDeliverySelection shouldBe VariantSelection.ALL
            repository.variantReads.get() shouldBe 0
            val generatedVariant = repository.uploadedVariant!!
            generatedVariant.attributes.width.value shouldBe 100
            assertAccess(generatedVariant.id)

            resetObservations()
            client.get("$path?width=100").apply {
                status shouldBe expectedStatus
                headers[APP_CACHE_STATUS] shouldBe "hit"
                bodyAsBytes()
            }
            assertDeliveryReads()
            repository.lastDeliverySelection shouldBe VariantSelection.ALL
            repository.variantReads.get() shouldBe 0
            repository.lastDeliveryAsset!!.variants shouldHaveSize 2
            assertAccess(generatedVariant.id)

            resetObservations()
            client.get(path).apply {
                status shouldBe expectedStatus
                headers[APP_CACHE_STATUS] shouldBe "hit"
                bodyAsBytes()
            }
            assertDeliveryReads()
            repository.lastDeliverySelection shouldBe VariantSelection.ORIGINAL_ONLY
            repository.variantReads.get() shouldBe 0
            val original = repository.lastDeliveryAsset!!.variants.single()
            original.isOriginalVariant shouldBe true
            assertAccess(original.id)
        }

    @Test
    fun `metadata returns all variants without extra reads`() =
        testInMemory(modules = listOf(observedModule)) {
            storeAssetMultipartSource(client, testImage().bytes, StoreAssetRequest())
            client.get("/assets/profile/-/link?width=100").status shouldBe HttpStatusCode.OK

            resetObservations()
            client.get("/assets/profile/-/info").body<AssetResponse>().variants shouldHaveSize 2
            repository.assetReads.get() shouldBe 1
            repository.assetForDeliveryReads.get() shouldBe 0
            repository.collectionReads.get() shouldBe 0
            repository.variantReads.get() shouldBe 0
            eventPublisher.accesses shouldHaveSize 0

            resetObservations()
            client
                .get("/assets/profile/-/entries")
                .body<AssetEntriesResponse>()
                .entries
                .single()
                .variants shouldHaveSize 2
            repository.collectionReads.get() shouldBe 1
            repository.assetReads.get() shouldBe 0
            repository.assetForDeliveryReads.get() shouldBe 0
            repository.variantReads.get() shouldBe 0
            eventPublisher.accesses shouldHaveSize 0
        }

    @Test
    fun `transformation equivalent to original is a cache hit`() =
        testInMemory(modules = listOf(observedModule)) {
            storeAssetMultipartSource(client, testImage(ImageFormat.PNG).bytes, StoreAssetRequest())
            resetObservations()

            client.get("/assets/profile/-/link?format=png").apply {
                status shouldBe HttpStatusCode.OK
                headers[APP_CACHE_STATUS] shouldBe "hit"
            }
            assertDeliveryReads()
            repository.variantReads.get() shouldBe 0
            repository.uploadedVariant shouldBe null
            assertAccess(
                repository.lastDeliveryAsset!!
                    .variants
                    .single()
                    .id,
            )
        }

    @ParameterizedTest
    @EnumSource(ReturnFormat::class, names = ["LINK", "REDIRECT", "CONTENT", "DOWNLOAD"])
    fun `missing transformed asset returns not found after one read`(returnFormat: ReturnFormat) =
        testInMemory(modules = listOf(observedModule)) {
            client.get("/assets/missing/-/${returnFormat.name.lowercase()}?width=100").status shouldBe HttpStatusCode.NotFound
            assertDeliveryReads()
            repository.variantReads.get() shouldBe 0
            eventPublisher.accesses shouldHaveSize 0
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
                resetObservations()
                val response = client.get("/assets/profile/-/$selection")
                response.status shouldBe HttpStatusCode.OK
                val link = response.body<AssetLinkResponse>()
                link.attributes.height shouldBe (original.height * 100.0 / original.width).roundToInt()
                repository.lastDeliveryAsset!!.entryId shouldBe first.second!!.entryId
                assertDeliveryReads()
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
            resetObservations()
            client.get("/assets/profile/-/link?width=100").status shouldBe HttpStatusCode.BadRequest
            assertDeliveryReads()
            repository.uploadedVariant shouldBe null
            eventPublisher.accesses shouldHaveSize 0
        }

    private fun resetObservations() {
        repository.resetReads()
        eventPublisher.reset()
    }

    private fun assertDeliveryReads(expected: Int = 1) {
        repository.assetForDeliveryReads.get() shouldBe expected
        repository.assetReads.get() shouldBe 0
        repository.collectionReads.get() shouldBe 0
    }

    private fun assertAccess(variantId: VariantId) {
        val accesses = eventPublisher.accesses
        accesses.map { it.variantId } shouldContainExactly listOf(variantId)
        accesses.single().path shouldBe "/profile"
    }
}
