package io.konifer.infrastructure.datastore

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.common.selector.Order
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetId
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.transformation.MetadataTransformation
import io.konifer.domain.transformation.PaddingTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.transformation.toPaddingAmount
import io.konifer.domain.transformation.toQuality
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantAlreadyExistsException
import io.konifer.domain.variant.retention.CacheProperties
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotEndWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit

abstract class AssetRepositoryTest {
    abstract fun createRepository(): AssetRepository

    val repository = createRepository()

    @Nested
    inner class StoreAssetTests {
        @Test
        fun `can store and fetch an asset`() =
            runTest {
                val pending =
                    createPendingAsset(
                        url = "https://localhost.com",
                    )
                val pendingPersisted = repository.storeNew(pending)
                repository.markReady(pendingPersisted.markReady(LocalDateTime.now(UTC)))
                pendingPersisted.apply {
                    path shouldBe "/users/123"
                    entryId shouldBe 0
                    labels.asMap() shouldContainExactly pending.labels.asMap()
                    tags.asSet() shouldContainExactly pending.tags.asSet()
                    source shouldBe pending.source
                    externalSourceAddress shouldBe pending.externalSourceAddress
                    createdAt shouldBe modifiedAt
                }
                val originalVariant = pendingPersisted.variants.first { it.isOriginalVariant }
                pendingPersisted.variants shouldHaveSize 1
                pendingPersisted.variants.first().apply {
                    attributes.height shouldBe originalVariant.attributes.height
                    attributes.width shouldBe originalVariant.attributes.width
                    this.attributes.format shouldBe originalVariant.attributes.format
                    this.transformation.height shouldBe originalVariant.attributes.height
                    this.transformation.width shouldBe originalVariant.attributes.width
                    this.transformation.format shouldBe originalVariant.attributes.format
                    this.transformation.fit shouldBe Fit.FIT
                    this.isOriginalVariant shouldBe true
                    this.lqips shouldBe LQIPs.NONE
                }
                val fetched = repository.fetchByPath(pendingPersisted.path, pendingPersisted.entryId, null, Order.NEW)

                fetched?.id shouldBe pendingPersisted.id
                fetched?.variants?.single()?.lastAccessedAt shouldNotBe null
            }

        @Test
        fun `can store and fetch an asset with null url`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now(UTC)))
                persisted.apply {
                    path shouldBe pending.path
                    entryId shouldBe 0
                    labels.asMap() shouldContainExactly pending.labels.asMap()
                    tags.asSet() shouldContainExactly pending.tags.asSet()
                    source shouldBe pending.source
                    externalSourceAddress shouldBe null
                    createdAt shouldBe modifiedAt
                }
                val fetched = repository.fetchByPath(persisted.path, persisted.entryId, null, Order.NEW)

                fetched?.id shouldBe persisted.id
            }

        @Test
        fun `can store and fetch an asset with trailing slash`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now(UTC)))
                persisted.path shouldNotEndWith "/"
                val fetched = repository.fetchByPath(persisted.path, persisted.entryId, null, Order.NEW)

                fetched?.id shouldBe persisted.id
                fetched!!.id shouldBe
                    repository.fetchByPath(persisted.path + "/", persisted.entryId, null, Order.NEW)?.id
            }

        @Test
        fun `storing an asset on an existent tree path appends the asset and increments entryId`() =
            runTest {
                val pending1 = createPendingAsset()
                val pending2 = createPendingAsset()
                val persisted1 = repository.storeNew(pending1)
                val persisted2 = repository.storeNew(pending2)

                persisted1.entryId shouldBe 0
                persisted2.entryId shouldBe 1
            }

        @Test
        fun `entryId is always the next highest value`() =
            runTest {
                val persisted1 = repository.storeNew(createPendingAsset())
                val persisted2 = repository.storeNew(createPendingAsset())
                persisted1.entryId shouldBe 0
                persisted2.entryId shouldBe 1
                repository.deleteByPath(
                    path = persisted1.path,
                    entryId = persisted1.entryId!!,
                )

                val pending3 = createPendingAsset()
                val persisted3 = repository.storeNew(pending3)
                persisted3.entryId shouldBe 2

                repository.deleteByPath(persisted2.path, entryId = persisted2.entryId!!)
                val pending4 = createPendingAsset()
                val persisted4 = repository.storeNew(pending4)
                persisted4.entryId shouldBe 3
            }
    }

    @Nested
    inner class StoreVariantTests {
        @Test
        fun `can store and fetch a variant`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now(UTC)))
                val attributes =
                    Attributes(
                        width = 10.toDimension(),
                        height = 10.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                        pageCount = 5,
                        loop = 0,
                    )

                val variantTransformation =
                    Transformation(
                        height = 10.toDimension(),
                        width = 10.toDimension(),
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                        colorSpace = ColorSpace.SRGB,
                    )
                val bucket = "bucket"
                val key = UuidCreator.getRandomBasedFast().toString()
                val newVariant =
                    repository.storeNewVariant(
                        createPendingVariant(
                            assetId = pending.id,
                            attributes = attributes,
                            transformation = variantTransformation,
                            objectStoreBucket = bucket,
                            objectStoreKey = key,
                        ),
                    )
                repository.markUploaded(
                    variant = newVariant.markReady(LocalDateTime.now(UTC)),
                    cacheProperties = CacheProperties(),
                )
                newVariant.assetId shouldBe persisted.id
                newVariant.apply {
                    this.attributes.height shouldBe attributes.height
                    this.attributes.width shouldBe attributes.width
                    this.attributes.format shouldBe attributes.format
                    this.attributes.colorSpace shouldBe attributes.colorSpace
                    this.attributes.pageCount shouldBe attributes.pageCount
                    this.attributes.loop shouldBe attributes.loop
                    this.transformation shouldBe variantTransformation
                    this.objectStoreBucket shouldBe bucket
                    this.objectStoreKey shouldBe key
                    this.expiresAt shouldBe null
                    this.isOriginalVariant shouldBe false
                }

                val assetData =
                    repository.fetchByPath(
                        persisted.path,
                        persisted.entryId,
                        null,
                        Order.NEW,
                    )
                assetData shouldNotBe null
                assetData!!.id shouldBe persisted.id
                assetData.variants shouldHaveSize 2
                assetData.variants.forAll { it.lastAccessedAt shouldNotBe null }
            }

        @Test
        fun `can store and fetch variant with expiry`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now(UTC)))
                val attributes =
                    Attributes(
                        width = 10.toDimension(),
                        height = 10.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                        pageCount = 5,
                        loop = 0,
                    )

                val expiry = LocalDateTime.now(UTC).plusHours(10)
                val variantTransformation =
                    Transformation(
                        height = 10.toDimension(),
                        width = 10.toDimension(),
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                        colorSpace = ColorSpace.SRGB,
                    )
                val newVariant =
                    repository.storeNewVariant(
                        createPendingVariant(
                            assetId = pending.id,
                            attributes = attributes,
                            transformation = variantTransformation,
                            objectStoreBucket = "bucket",
                            objectStoreKey = UuidCreator.getRandomBasedFast().toString(),
                            expiresAt = expiry,
                        ),
                    )
                repository.markUploaded(
                    variant = newVariant.markReady(LocalDateTime.now(UTC)),
                    cacheProperties = CacheProperties(),
                )
                newVariant.assetId shouldBe persisted.id
                newVariant.apply {
                    this.expiresAt?.toEpochSecond(UTC) shouldBe expiry.toEpochSecond(UTC)
                }

                val assetData =
                    repository.fetchByPath(
                        persisted.path,
                        persisted.entryId,
                        null,
                        Order.NEW,
                    )
                assetData shouldNotBe null
                assetData!!.id shouldBe persisted.id
                assetData.variants shouldHaveSize 2
            }

        @Test
        fun `cannot store a variant of an asset that does not exist`() =
            runTest {
                shouldThrow<IllegalArgumentException> {
                    repository.storeNewVariant(
                        createPendingVariant(
                            assetId = AssetId(),
                            transformation =
                                Transformation(
                                    height = 100.toDimension(),
                                    width = 100.toDimension(),
                                    format = ImageFormat.PNG,
                                    colorSpace = ColorSpace.SRGB,
                                ),
                        ),
                    )
                }
            }

        @Test
        fun `cannot store a duplicate variant`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                val attributes =
                    Attributes(
                        width = 50.toDimension(),
                        height = 100.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                    )

                val transformation =
                    Transformation(
                        height = 50.toDimension(),
                        width = 100.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        attributes = attributes,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                persistedVariant.assetId shouldBe persisted.id
                persistedVariant.apply {
                    this.transformation shouldBe transformation
                    this.attributes shouldBe attributes
                    objectStoreBucket shouldBe pendingVariant.objectStoreBucket
                    objectStoreKey shouldBe pendingVariant.objectStoreKey
                    isOriginalVariant shouldBe false
                }

                shouldThrow<VariantAlreadyExistsException> {
                    repository.storeNewVariant(pendingVariant)
                }
            }
    }

    @Nested
    inner class FetchByPathTests {
        @Test
        fun `fetching asset that does not exist returns null`() =
            runTest {
                repository.fetchByPath("/doesNotExist", null, null, Order.NEW) shouldBe null
            }

        @Test
        fun `fetching asset with entryId that does not exist returns null`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.fetchByPath(persisted.path, persisted.entryId!! + 1, null, Order.NEW) shouldBe null
            }

        @Test
        fun `ready filtering can be disabled without discarding the requested path`() =
            runTest {
                val requested =
                    repository.storeNew(
                        createPendingAsset(path = "/requested/pending"),
                    )
                repository.storeNew(
                    createPendingAsset(path = "/other/pending"),
                )

                val fetched =
                    repository.fetchByPath(
                        path = requested.path,
                        entryId = requested.entryId,
                        transformation = null,
                        includeOnlyReady = false,
                    )

                fetched shouldNotBe null
                fetched!!.id shouldBe requested.id
                fetched.path shouldBe requested.path
            }

        @Test
        fun `returns an existing asset`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                val ready = persisted.markReady(LocalDateTime.now(UTC))
                repository.markReady(ready)
                val fetched = repository.fetchByPath(persisted.path, persisted.entryId, null, Order.NEW)

                assertFetchedAgainstAggregate(fetched, ready, true)
            }

        @Test
        fun `returns last created asset if multiple exist`() =
            runTest {
                val pending1 = createPendingAsset()
                val pending2 = createPendingAsset()
                repository.storeNew(pending1)
                val persisted2 = repository.storeNew(pending2)
                repository.markReady(persisted2.markReady(LocalDateTime.now(UTC)))

                repository.fetchByPath(pending1.path, entryId = null, transformation = null, Order.NEW)?.id shouldBe persisted2.id
            }

        @Test
        fun `returns an existing asset by entryId`() =
            runTest {
                val pending1 = createPendingAsset()
                val pending2 = createPendingAsset()
                val persisted1 = repository.storeNew(pending1)
                repository.markReady(persisted1.markReady(LocalDateTime.now(UTC)))
                val persisted2 = repository.storeNew(pending2)
                repository.markReady(persisted2.markReady(LocalDateTime.now(UTC)))

                repository.fetchByPath(persisted1.path, entryId = persisted1.entryId!!, transformation = null, Order.NEW)?.id shouldBe
                    persisted1.id
                repository.fetchByPath(persisted2.path, entryId = persisted2.entryId!!, transformation = null, Order.NEW)?.id shouldBe
                    persisted2.id
            }

        @Test
        fun `returns null if there is no asset in path at specific entryId`() =
            runTest {
                val pending = createPendingAsset()
                repository.storeNew(pending)
                repository.fetchByPath(pending.path, entryId = 1, transformation = null, Order.NEW) shouldBe null
            }

        @Test
        fun `can fetch original variant with matching transformation`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now(UTC)))
                val originalVariantTransformation =
                    Transformation(
                        height =
                            pending.variants
                                .first()
                                .attributes.height,
                        width =
                            pending.variants
                                .first()
                                .attributes.width,
                        format =
                            pending.variants
                                .first()
                                .attributes.format,
                        fit = Fit.FIT,
                        colorSpace = ColorSpace.SRGB,
                    )

                val assetData =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = originalVariantTransformation,
                        order = Order.NEW,
                    )
                assetData shouldNotBe null
                assetData!!.id shouldBe persisted.id
                assetData.variants shouldHaveSize 1
                assetData.variants.first().apply {
                    isOriginalVariant shouldBe true
                }
            }

        @Test
        fun `returns no asset at path if none have requested labels`() =
            runTest {
                val ready =
                    storeReadyAsset(
                        createPendingAsset(
                            labels =
                                mapOf(
                                    "phone" to "iphone",
                                    "hello" to "world",
                                ),
                        ),
                    )

                repository.fetchByPath(
                    path = ready.path,
                    entryId = null,
                    transformation = null,
                    labels = mapOf("phone" to "android"),
                ) shouldBe null
            }

        @ParameterizedTest(name = "matchAllLabels={0}")
        @ValueSource(booleans = [true, false])
        fun `returns asset matching requested labels`(matchAllLabels: Boolean) =
            runTest {
                val storedLabels =
                    mapOf(
                        "phone" to "iphone",
                        "hello" to "world",
                    )
                val matching = storeReadyAsset(createPendingAsset(labels = storedLabels))
                storeReadyAsset(
                    createPendingAsset(
                        labels =
                            mapOf(
                                "phone" to "android",
                            ),
                    ),
                )
                val requestedLabels =
                    if (matchAllLabels) {
                        storedLabels
                    } else {
                        mapOf("phone" to "iphone")
                    }

                val fetched =
                    checkNotNull(
                        repository.fetchByPath(
                            path = matching.path,
                            entryId = null,
                            transformation = null,
                            labels = requestedLabels,
                        ),
                    )
                fetched.id shouldBe matching.id
                fetched.variants.single().isOriginalVariant shouldBe true
                fetched.labels shouldContainExactly storedLabels
            }

        @Test
        fun `returns assets ordered by modifiedAt if specified`() =
            runTest {
                // Test labels in case ordering doesn't work with joins for some reason
                val labels =
                    mapOf(
                        "phone" to "iphone",
                    )
                val pending1 = createPendingAsset(labels = labels)
                val persisted1 = repository.storeNew(pending1)
                repository.markReady(persisted1.markReady(LocalDateTime.now(UTC)))
                val pending2 = createPendingAsset(labels = labels)
                val persisted2 = repository.storeNew(pending2)
                repository.markReady(persisted2.markReady(LocalDateTime.now(UTC)))
                val ready1 =
                    persisted1.markReady(LocalDateTime.now(UTC)).update(
                        alt = "I'm updated!!",
                        tags = persisted1.tags.asSet(),
                        labels = persisted1.labels.asMap(),
                    )
                val updated1 = repository.update(ready1)
                repository
                    .fetchByPath(
                        path = updated1.path,
                        entryId = null,
                        transformation = null,
                        order = Order.MODIFIED,
                    )?.id shouldBe updated1.id
                repository
                    .fetchByPath(
                        path = persisted1.path,
                        entryId = null,
                        transformation = null,
                        order = Order.NEW,
                    )?.id shouldBe persisted2.id
            }

        @Test
        fun `does not return variant that is in pending state`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now(UTC)))
                val transformation =
                    Transformation(
                        height = 10.toDimension(),
                        width = 10.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                    )
                val variant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                repository.storeNewVariant(variant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset?.id shouldBe persisted.id
                fetchedAsset!!.variants shouldHaveSize 0
            }
    }

    @Nested
    inner class FetchAllByPathTests {
        @Test
        fun `returns asset at path`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                val ready = persisted.markReady(LocalDateTime.now(UTC))
                repository.markReady(ready)

                repository.fetchAllByPath("/users/123", null, limit = 1).apply {
                    this shouldHaveSize 1
                    assertFetchedAgainstAggregate(first(), ready, true)
                }
            }

        @Test
        fun `returns all assets at path ordered correctly`() =
            runTest {
                val first = storeReadyAsset()
                val second = storeReadyAsset()
                val updatedFirst =
                    first.update(
                        alt = "I'm updated!!",
                        tags = first.tags.asSet(),
                        labels = first.labels.asMap(),
                    )
                repository.update(updatedFirst)

                repository
                    .fetchAllByPath("/users/123", null, order = Order.NEW, limit = 10)
                    .map { it.id } shouldContainExactly listOf(second.id, first.id)
                repository
                    .fetchAllByPath("/users/123", null, order = Order.MODIFIED, limit = 10)
                    .map { it.id } shouldContainExactly listOf(first.id, second.id)
            }

        @Test
        fun `returns no assets at path if none have requested labels`() =
            runTest {
                val assets = storeReadyAssets(count = 2) { createPendingAsset(labels = emptyMap()) }

                repository.fetchAllByPath(
                    path = assets.first().path,
                    transformation = null,
                    labels =
                        mapOf(
                            "phone" to "iphone",
                            "hello" to "world",
                        ),
                    limit = 10,
                ) shouldBe emptyList()
            }

        @ParameterizedTest(name = "matchAllLabels={0}")
        @ValueSource(booleans = [true, false])
        fun `returns assets matching requested labels`(matchAllLabels: Boolean) =
            runTest {
                val storedLabels =
                    mapOf(
                        "phone" to "iphone",
                        "hello" to "world",
                    )
                val matching = storeReadyAssets(count = 2) { createPendingAsset(labels = storedLabels) }
                storeReadyAsset(createPendingAsset(labels = mapOf("phone" to "android")))
                val requestedLabels =
                    if (matchAllLabels) {
                        storedLabels
                    } else {
                        mapOf("phone" to "iphone")
                    }

                val fetched =
                    repository.fetchAllByPath(
                        path = "/users/123",
                        transformation = null,
                        labels = requestedLabels,
                        limit = 10,
                    )
                fetched.map { it.id } shouldContainExactly matching.map { it.id }.reversed()
            }

        @Test
        fun `returns all assets even if they do not have a requested variant`() =
            runTest {
                val assets = storeReadyAssets(count = 3)
                assets.forEach { asset ->
                    repository.storeNewVariant(
                        createPendingVariant(
                            assetId = asset.id,
                            transformation =
                                Transformation(
                                    height = 10.toDimension(),
                                    width = 10.toDimension(),
                                    format = ImageFormat.PNG,
                                    colorSpace = ColorSpace.SRGB,
                                ),
                        ),
                    )
                }
                val transformation =
                    Transformation(
                        width = 8.toDimension(),
                        height = 5.toDimension(),
                        format = ImageFormat.JPEG,
                        fit = Fit.FIT,
                        colorSpace = ColorSpace.SRGB,
                    )

                val fetched = repository.fetchAllByPath("/users/123", transformation, limit = 10)
                fetched shouldHaveSize assets.size
                fetched.forAll {
                    it.variants shouldHaveSize 0
                }
            }

        @ParameterizedTest(name = "requestSpecificVariant={0}")
        @ValueSource(booleans = [true, false])
        fun `returns all assets with the requested variants`(requestSpecificVariant: Boolean) =
            runTest {
                val transformation =
                    Transformation(
                        height = 10.toDimension(),
                        width = 10.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                    )
                val assets = storeReadyAssets(count = 3)
                assets.forEach { asset ->
                    storeReadyVariant(asset, transformation)
                }

                val requestedTransformation = if (requestSpecificVariant) transformation else null
                val fetched = repository.fetchAllByPath("/users/123", requestedTransformation, limit = 10)
                fetched shouldHaveSize assets.size
                fetched.forAll {
                    if (requestSpecificVariant) {
                        it.variants.single().transformation shouldBe transformation
                    } else {
                        it.variants shouldHaveSize 2
                        it.variants.any { variant -> variant.isOriginalVariant } shouldBe true
                        it.variants.any { variant -> variant.transformation == transformation } shouldBe true
                    }
                }
            }

        @Test
        fun `returns empty list if no assets in path`() =
            runTest {
                repository.fetchAllByPath("/users/123", null, limit = 10) shouldBe emptyList()
            }

        @ParameterizedTest(name = "limit={0}, expectedCount={1}")
        @CsvSource("5, 5", "-1, 10")
        fun `limit is respected`(
            limit: Int,
            expectedCount: Int,
        ) = runTest {
            storeReadyAssets(count = 10)
            repository.fetchAllByPath(
                path = "/users/123",
                transformation = null,
                limit = limit,
            ) shouldHaveSize expectedCount
        }

        @Test
        fun `does not return assets that are not ready`() =
            runTest {
                repeat(10) {
                    repository.storeNew(createPendingAsset())
                }
                repository.fetchAllByPath(
                    path = "/users/123",
                    transformation = null,
                    limit = 10,
                ) shouldHaveSize 0
            }
    }

    @Nested
    inner class DeleteByPathTests {
        @Test
        fun `deletes an asset`() =
            runTest {
                val ready = storeReadyAsset()
                repository.deleteByPath(
                    path = "/users/123",
                    entryId = 0,
                )

                repository.fetchByPath(ready.path, ready.entryId, null, Order.NEW) shouldBe null
                repository.fetchByPath(
                    "/users/123",
                    entryId = null,
                    transformation = null,
                    Order.NEW,
                ) shouldBe null
            }

        @Test
        fun `returns does nothing if no assets exist in path`() =
            runTest {
                shouldNotThrowAny {
                    repository.deleteByPath(
                        path = "/users/123",
                        entryId = 0,
                    )
                }
            }

        @Test
        fun `returns does nothing if asset does not exist at specific entryId`() =
            runTest {
                val ready = storeReadyAsset()
                shouldNotThrowAny {
                    repository.deleteByPath("/users/123", entryId = 1)
                }

                repository.fetchByPath(ready.path, ready.entryId, null, Order.NEW)?.id shouldBe
                    ready.id
                repository.fetchAllByPath("/users/123", null, limit = 10).apply {
                    this shouldHaveSize 1
                    first().id shouldBe ready.id
                }
            }
    }

    @Nested
    inner class DeleteAllByPathTests {
        @Test
        fun `limit is respected when deleting assets at path`() =
            runTest {
                val ready1 = storeReadyAsset()
                val ready2 = storeReadyAsset()

                repository.deleteAllByPath("/users/123", limit = 1)

                repository.fetchByPath(
                    path = ready1.path,
                    entryId = ready1.entryId,
                    transformation = null,
                    order = Order.NEW,
                ) shouldNotBe null
                repository.fetchByPath(
                    path = ready2.path,
                    entryId = ready2.entryId,
                    transformation = null,
                    order = Order.NEW,
                ) shouldBe null
                repository.fetchAllByPath(
                    path = "/users/123",
                    transformation = null,
                    limit = 10,
                ) shouldHaveSize 1
            }

        @Test
        fun `deletes all assets at path`() =
            runTest {
                val ready1 = storeReadyAsset()
                val ready2 = storeReadyAsset()

                repository.deleteAllByPath("/users/123", limit = -1)

                repository.fetchByPath(ready1.path, ready1.entryId, null, Order.NEW) shouldBe null
                repository.fetchByPath(ready2.path, ready2.entryId, null, Order.NEW) shouldBe null
                repository.fetchAllByPath("/users/123", null, limit = 10) shouldBe emptyList()
            }

        @Test
        fun `orderBy is respected when deleting assets at path`() =
            runTest {
                val ready1 = storeReadyAsset()
                val ready2 = storeReadyAsset()
                val updated =
                    repository.update(
                        ready1.update(
                            alt = "updated",
                            labels = emptyMap(),
                            tags = emptySet(),
                        ),
                    )
                updated.modifiedAt shouldBeAfter ready1.modifiedAt

                repository.deleteAllByPath("/users/123", limit = 1, order = Order.MODIFIED)

                repository.fetchByPath(ready1.path, ready1.entryId, null, Order.NEW) shouldBe null
                repository.fetchByPath(ready2.path, ready2.entryId, null, Order.NEW) shouldNotBe null
                repository.fetchAllByPath("/users/123", null, limit = 10) shouldHaveSize 1
            }

        @Test
        fun `deletes nothing if no assets have supplied labels`() =
            runTest {
                val ready = storeReadyAsset(createPendingAsset(labels = mapOf("animal" to "cat")))

                repository.deleteAllByPath("/users/123", labels = mapOf("animal" to "dog"), limit = 1)

                repository.fetchByPath(
                    path = ready.path,
                    entryId = ready.entryId,
                    transformation = null,
                    order = Order.NEW,
                ) shouldNotBe null
            }

        @ParameterizedTest(name = "storedLabelsAreSuperset={0}")
        @ValueSource(booleans = [true, false])
        fun `deletes assets containing the requested labels`(storedLabelsAreSuperset: Boolean) =
            runTest {
                val labels =
                    buildMap {
                        put("animal", "cat")
                        if (storedLabelsAreSuperset) put("phone", "iphone")
                    }
                val deleted = storeReadyAsset(createPendingAsset(labels = labels))
                val retained = storeReadyAsset(createPendingAsset(labels = mapOf("animal" to "dog")))

                repository.deleteAllByPath("/users/123", labels = mapOf("animal" to "cat"), limit = 1)

                repository.fetchByPath(
                    path = deleted.path,
                    entryId = deleted.entryId,
                    transformation = null,
                    order = Order.NEW,
                ) shouldBe null
                repository.fetchByPath(
                    path = retained.path,
                    entryId = retained.entryId,
                    transformation = null,
                    order = Order.NEW,
                ) shouldNotBe null
                repository.fetchAllByPath(
                    path = "/users/123",
                    transformation = null,
                    limit = 10,
                ) shouldHaveSize 1
            }

        @Test
        fun `does nothing if nothing exists at path`() =
            runTest {
                shouldNotThrowAny {
                    repository.deleteAllByPath(
                        path = "/users/123",
                        limit = -1,
                    )
                }
            }
    }

    @Nested
    inner class DeleteRecursivelyByPathTests {
        @Test
        fun `deletes all assets at path recursively`() =
            runTest {
                val ready1 = storeReadyAsset(createPendingAsset(path = "users/123"))
                val ready2 = storeReadyAsset(createPendingAsset(path = "users/123"))
                val ready3 = storeReadyAsset(createPendingAsset(path = "users/123/profile"))

                repository.deleteRecursivelyByPath("/users/123")

                repository.fetchByPath(ready1.path, ready1.entryId, null, Order.NEW) shouldBe null
                repository.fetchByPath(ready2.path, ready2.entryId, null, Order.NEW) shouldBe null
                repository.fetchByPath(ready3.path, ready3.entryId, null, Order.NEW) shouldBe null
                repository.fetchAllByPath("/users/123", null, limit = -1) shouldBe emptyList()
                repository.fetchAllByPath("users/123/profile", null, limit = -1) shouldBe emptyList()
            }

        @Test
        fun `deletes assets recursively with supplied labels`() =
            runTest {
                val ready1 =
                    storeReadyAsset(
                        createPendingAsset(
                            path = "/users/123",
                            labels = mapOf("animal" to "cat"),
                        ),
                    )
                val ready2 = storeReadyAsset(createPendingAsset(path = "/users/123"))
                val ready3 =
                    storeReadyAsset(
                        createPendingAsset(
                            labels = mapOf("animal" to "cat"),
                            path = "/users/123/photo",
                        ),
                    )
                val ready4 = storeReadyAsset(createPendingAsset(path = "/users/123/photo"))

                repository.deleteRecursivelyByPath("/users/123", labels = mapOf("animal" to "cat"))

                repository.fetchByPath(
                    path = ready1.path,
                    entryId = ready1.entryId,
                    transformation = null,
                    order = Order.NEW,
                ) shouldBe null
                repository.fetchByPath(
                    path = ready2.path,
                    entryId = ready2.entryId,
                    transformation = null,
                    order = Order.NEW,
                ) shouldNotBe null
                repository.fetchByPath(
                    path = ready3.path,
                    entryId = ready3.entryId,
                    transformation = null,
                    order = Order.NEW,
                ) shouldBe null
                repository.fetchByPath(
                    path = ready4.path,
                    entryId = ready4.entryId,
                    transformation = null,
                    order = Order.NEW,
                ) shouldNotBe null
            }

        @Test
        fun `does nothing if nothing exists at path`() =
            runTest {
                shouldNotThrowAny {
                    repository.deleteRecursivelyByPath(
                        path = "/users/123",
                    )
                }
            }
    }

    /**
     * Verifies exact transformation matching. Each case stores one ready variant, then checks both a matching
     * transformation and a transformation that differs by one relevant property.
     */
    @Nested
    inner class FetchVariantByTransformationTests {
        @ParameterizedTest(name = "{0}")
        @MethodSource("io.konifer.infrastructure.datastore.AssetRepositoryTestDataProviders#transformationLookupSource")
        fun `returns variant only for an exact transformation match`(case: TransformationLookupCase) =
            runTest {
                val asset = storeReadyAsset()
                val variant = storeReadyVariant(asset, case.stored)

                val matching =
                    checkNotNull(
                        repository.fetchByPath(
                            path = asset.path,
                            entryId = asset.entryId,
                            transformation = case.stored,
                        ),
                    )
                matching.variants.map { it.id } shouldContainExactly listOf(variant.id)

                val nonMatching =
                    checkNotNull(
                        repository.fetchByPath(
                            path = asset.path,
                            entryId = asset.entryId,
                            transformation = case.nonMatching,
                        ),
                    )
                nonMatching.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch variant by all transformations at once`() =
            runTest {
                val transformation =
                    Transformation(
                        height = 10.toDimension(),
                        width = 10.toDimension(),
                        format = ImageFormat.PNG,
                        horizontalFlip = true,
                        rotate = Rotate.ONE_HUNDRED_EIGHTY,
                        fit = Fit.STRETCH,
                        filter = Filter.SEPIA,
                        gravity = Gravity.ENTROPY,
                        quality = 50.toQuality(),
                        padding =
                            PaddingTransformation(
                                amount = 10.toPaddingAmount(),
                                color = listOf(100, 50, 34, 100),
                            ),
                        metadata =
                            MetadataTransformation(
                                strip = setOf(MetadataType.EXIF, MetadataType.XMP, MetadataType.IPTC),
                            ),
                        colorSpace = ColorSpace.SRGB,
                    )
                val asset = storeReadyAsset()
                val variant = storeReadyVariant(asset, transformation)

                val fetched =
                    checkNotNull(
                        repository.fetchByPath(
                            path = asset.path,
                            entryId = asset.entryId,
                            transformation = transformation,
                        ),
                    )
                fetched.variants.map { it.id } shouldContainExactly listOf(variant.id)
            }
    }

    @Nested
    inner class UpdateTests {
        @Test
        fun `can update attributes of asset`() =
            runTest {
                val pending = createPendingAsset()
                val ready =
                    repository
                        .storeNew(pending)
                        .markReady(uploadedAt = LocalDateTime.now(UTC))
                repository.markReady(ready)
                val updated =
                    ready.update(
                        alt = "updated alt",
                        labels =
                            mapOf(
                                "updated" to "updated-value",
                                "updated-phone" to "updated-iphone",
                            ),
                        tags = setOf("updated-tag1", "updated-tag2"),
                    )
                val actual = repository.update(updated)

                actual.variants shouldHaveSize 1
                actual.variants.first().id shouldBe updated.variants.first().id
                actual.apply {
                    alt shouldBe updated.alt
                    labels.asMap() shouldContainExactly updated.labels.asMap()
                    tags.asSet() shouldContainExactly updated.tags.asSet()
                    modifiedAt shouldBeAfter ready.modifiedAt
                    modifiedAt.truncatedTo(ChronoUnit.MILLIS) shouldBe updated.modifiedAt.truncatedTo(ChronoUnit.MILLIS)
                }
            }
    }

    @Nested
    inner class MarkReadyTests {
        @Test
        fun `asset is marked ready`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)

                val uploadedAt = LocalDateTime.now(UTC)
                val ready =
                    persisted.markReady(uploadedAt = uploadedAt).let {
                        repository.markReady(it)
                        repository.fetchByPath(
                            path = it.path,
                            entryId = it.entryId!!,
                            transformation = null,
                        )
                    }

                ready shouldNotBe null
                ready!!.isReady shouldBe true
                ready.modifiedAt shouldBeAfter persisted.modifiedAt
                ready.variants shouldHaveSize 1
                ready.variants
                    .first()
                    .uploadedAt
                    ?.truncatedTo(ChronoUnit.MILLIS) shouldBe uploadedAt.truncatedTo(ChronoUnit.MILLIS)
                ready.variants.first().lastAccessedAt shouldNotBe null
            }
    }

    @Nested
    inner class MarkUploadedTests {
        @Test
        fun `can mark variant as uploaded`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)

                val ready =
                    persisted.markReady(uploadedAt = LocalDateTime.now(UTC)).let {
                        repository.markReady(it)
                        repository.fetchByPath(
                            path = it.path,
                            entryId = it.entryId!!,
                            transformation = null,
                        )
                    }
                val transformation =
                    Transformation(
                        format = ImageFormat.HEIC,
                        height = 400.toDimension(),
                        width = 400.toDimension(),
                        colorSpace = ColorSpace.SRGB,
                    )

                val pendingVariant =
                    createPendingVariant(
                        assetId = pending.id,
                        transformation = transformation,
                    )

                val persistedVariant = repository.storeNewVariant(pendingVariant)
                persistedVariant.uploadedAt shouldBe null
                val uploadedAt = LocalDateTime.now(UTC)
                val readyVariant =
                    repository
                        .markUploaded(
                            variant = persistedVariant.markReady(uploadedAt),
                            cacheProperties = CacheProperties(),
                        ).let {
                            repository.fetchByPath(
                                path = ready!!.path,
                                entryId = ready.entryId,
                                transformation = transformation,
                            )
                        }
                readyVariant shouldNotBe null
                readyVariant!!.variants shouldHaveSize 1
                readyVariant.variants.first().transformation shouldBe transformation
                readyVariant.variants
                    .first()
                    .uploadedAt
                    ?.truncatedTo(ChronoUnit.MILLIS) shouldBe
                    uploadedAt.truncatedTo(
                        ChronoUnit.MILLIS,
                    )
            }

        @Test
        fun `marking a variant uploaded does not mark other variants uploaded`() =
            runTest {
                val originalUploadedAt = LocalDateTime.now(UTC).minusMinutes(1)
                val persisted = repository.storeNew(createPendingAsset())
                repository.markReady(persisted.markReady(originalUploadedAt))

                val firstTransformation =
                    Transformation(
                        format = ImageFormat.WEBP,
                        height = 200.toDimension(),
                        width = 200.toDimension(),
                        colorSpace = ColorSpace.SRGB,
                    )
                val secondTransformation =
                    Transformation(
                        format = ImageFormat.WEBP,
                        height = 400.toDimension(),
                        width = 400.toDimension(),
                        colorSpace = ColorSpace.SRGB,
                    )
                val firstVariant =
                    repository.storeNewVariant(
                        createPendingVariant(
                            assetId = persisted.id,
                            transformation = firstTransformation,
                        ),
                    )
                val secondVariant =
                    repository.storeNewVariant(
                        createPendingVariant(
                            assetId = persisted.id,
                            transformation = secondTransformation,
                        ),
                    )
                val firstUploadedAt = LocalDateTime.now(UTC)
                repository.markUploaded(
                    variant = firstVariant.markReady(firstUploadedAt),
                    cacheProperties = CacheProperties(),
                )

                val fetched =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = null,
                    )
                fetched shouldNotBe null
                fetched!!.variants shouldHaveSize 2
                fetched.variants
                    .first { it.isOriginalVariant }
                    .uploadedAt
                    ?.truncatedTo(ChronoUnit.MILLIS) shouldBe originalUploadedAt.truncatedTo(ChronoUnit.MILLIS)
                fetched.variants
                    .first { it.id == firstVariant.id }
                    .uploadedAt
                    ?.truncatedTo(ChronoUnit.MILLIS) shouldBe firstUploadedAt.truncatedTo(ChronoUnit.MILLIS)
                fetched.variants.find { it.id == secondVariant.id } shouldBe null

                repository
                    .fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = secondTransformation,
                    )!!
                    .variants shouldHaveSize 0
            }

        @ParameterizedTest
        @ValueSource(ints = [1, 5])
        fun `mark uploaded evicts a variant if the max is exceeded`(maxVariants: Int) =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)

                persisted.markReady(uploadedAt = LocalDateTime.now(UTC)).let {
                    repository.markReady(it)
                }

                repeat(maxVariants) { idx ->
                    val transformation =
                        Transformation(
                            format = ImageFormat.HEIC,
                            height = (400 + idx).toDimension(),
                            width = (400 + idx).toDimension(),
                            colorSpace = ColorSpace.SRGB,
                        )
                    storeReadyVariant(
                        asset = persisted,
                        transformation = transformation,
                        maxVariants = maxVariants,
                    )
                }
                val variantsBeforeEviction =
                    repository
                        .fetchByPath(
                            path = persisted.path,
                            entryId = persisted.entryId!!,
                            transformation = null,
                        )?.variants shouldNotBe null
                variantsBeforeEviction!! shouldHaveSize maxVariants + 1 // including original variant

                val transformation =
                    Transformation(
                        format = ImageFormat.HEIC,
                        height = (400 - maxVariants).toDimension(),
                        width = (400 - maxVariants).toDimension(),
                        colorSpace = ColorSpace.SRGB,
                    )
                storeReadyVariant(
                    asset = persisted,
                    transformation = transformation,
                    maxVariants = maxVariants,
                )
                val variantsAfterEviction =
                    repository
                        .fetchByPath(
                            path = persisted.path,
                            entryId = persisted.entryId,
                            transformation = null,
                        )?.variants shouldNotBe null
                variantsAfterEviction!! shouldHaveSize maxVariants + 1 // including original variant
            }

        @Test
        fun `mark uploaded removes every cached variant beyond a reduced max`() =
            runTest {
                val persisted = repository.storeNew(createPendingAsset())
                repository.markReady(persisted.markReady(uploadedAt = LocalDateTime.now(UTC)))

                val previouslyReadyVariantIds =
                    (1..3).map { index ->
                        storeReadyVariant(
                            asset = persisted,
                            transformation =
                                Transformation(
                                    format = ImageFormat.HEIC,
                                    height = (500 + index).toDimension(),
                                    width = (500 + index).toDimension(),
                                    colorSpace = ColorSpace.SRGB,
                                ),
                            maxVariants = 3,
                        ).id
                    }

                val newlyUploadedVariantId =
                    storeReadyVariant(
                        asset = persisted,
                        transformation =
                            Transformation(
                                format = ImageFormat.HEIC,
                                height = 700.toDimension(),
                                width = 700.toDimension(),
                                colorSpace = ColorSpace.SRGB,
                            ),
                        maxVariants = 1,
                    ).id

                val retainedAssetData =
                    checkNotNull(
                        repository.fetchByPath(
                            path = persisted.path,
                            entryId = persisted.entryId,
                            transformation = null,
                        ),
                    )
                val retainedVariantIds = retainedAssetData.variants.map { it.id }

                retainedVariantIds shouldContainExactlyInAnyOrder
                    listOf(
                        persisted.variants.single().id,
                        newlyUploadedVariantId,
                    )
                retainedVariantIds.none { it in previouslyReadyVariantIds } shouldBe true
            }

        @Test
        fun `pending variants do not count toward the max`() =
            runTest {
                val persisted = repository.storeNew(createPendingAsset())
                repository.markReady(persisted.markReady(uploadedAt = LocalDateTime.now(UTC)))

                val firstReadyVariantId =
                    storeReadyVariant(
                        asset = persisted,
                        transformation =
                            Transformation(
                                format = ImageFormat.HEIC,
                                height = 800.toDimension(),
                                width = 800.toDimension(),
                                colorSpace = ColorSpace.SRGB,
                            ),
                        maxVariants = 2,
                    ).id
                repository.storeNewVariant(
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation =
                            Transformation(
                                format = ImageFormat.HEIC,
                                height = 900.toDimension(),
                                width = 900.toDimension(),
                                colorSpace = ColorSpace.SRGB,
                            ),
                    ),
                )

                val secondReadyVariantId =
                    storeReadyVariant(
                        asset = persisted,
                        transformation =
                            Transformation(
                                format = ImageFormat.HEIC,
                                height = 1_000.toDimension(),
                                width = 1_000.toDimension(),
                                colorSpace = ColorSpace.SRGB,
                            ),
                        maxVariants = 2,
                    ).id

                val retainedVariantIds =
                    checkNotNull(
                        repository.fetchByPath(
                            path = persisted.path,
                            entryId = persisted.entryId,
                            transformation = null,
                        ),
                    ).variants.map { it.id }

                retainedVariantIds shouldContainExactlyInAnyOrder
                    listOf(
                        persisted.variants.single().id,
                        firstReadyVariantId,
                        secondReadyVariantId,
                    )
            }
    }

    protected suspend fun storeReadyVariant(
        asset: Asset,
        transformation: Transformation,
        maxVariants: Int = CacheProperties().maxVariants,
    ): Variant.Ready {
        val pendingVariant =
            createPendingVariant(
                assetId = asset.id,
                transformation = transformation,
            )
        val persistedVariant = repository.storeNewVariant(pendingVariant)
        val readyVariant = persistedVariant.markReady(LocalDateTime.now(UTC))
        repository.markUploaded(
            variant = readyVariant,
            cacheProperties = CacheProperties(maxVariants = maxVariants),
        )
        return readyVariant
    }

    protected suspend fun storeReadyAsset(
        pending: Asset.Pending = createPendingAsset(),
        uploadedAt: LocalDateTime = LocalDateTime.now(UTC),
    ): Asset.Ready {
        val ready = repository.storeNew(pending).markReady(uploadedAt)
        repository.markReady(ready)
        return ready
    }

    protected suspend fun storeReadyAssets(
        count: Int,
        pendingFactory: (Int) -> Asset.Pending = { createPendingAsset() },
    ): List<Asset.Ready> =
        List(count) { index ->
            storeReadyAsset(pendingFactory(index))
        }
}
