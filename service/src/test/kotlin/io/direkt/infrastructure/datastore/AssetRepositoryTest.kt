package io.direkt.infrastructure.datastore

import io.direkt.domain.asset.AssetId
import io.direkt.domain.image.Filter
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Rotate
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation
import io.direkt.service.context.modifiers.OrderBy
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
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
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

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
                repository.markReady(pendingPersisted.markReady(LocalDateTime.now()))
                pendingPersisted.apply {
                    path shouldBe "/users/123"
                    entryId shouldBe 0
                    labels shouldContainExactly pending.labels
                    tags shouldContainExactly pending.tags
                    source shouldBe pending.source
                    sourceUrl shouldBe pending.sourceUrl
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
                val fetched = repository.fetchByPath(pendingPersisted.path, pendingPersisted.entryId, null, OrderBy.CREATED)

                fetched?.id shouldBe pendingPersisted.id
            }

        @Test
        fun `can store and fetch an asset with null url`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                persisted.apply {
                    path shouldBe pending.path
                    entryId shouldBe 0
                    labels shouldContainExactly pending.labels
                    tags shouldContainExactly pending.tags
                    source shouldBe pending.source
                    sourceUrl shouldBe null
                    createdAt shouldBe modifiedAt
                }
                val fetched = repository.fetchByPath(persisted.path, persisted.entryId, null, OrderBy.CREATED)

                fetched?.id shouldBe persisted.id
            }

        @Test
        fun `can store and fetch an asset with trailing slash`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                persisted.path shouldNotEndWith "/"
                val fetched = repository.fetchByPath(persisted.path, persisted.entryId, null, OrderBy.CREATED)

                fetched?.id shouldBe persisted.id
                fetched!!.id shouldBe
                    repository.fetchByPath(persisted.path + "/", persisted.entryId, null, OrderBy.CREATED)?.id
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
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val attributes =
                    Attributes(
                        width = 10,
                        height = 10,
                        format = ImageFormat.PNG,
                    )

                val variantTransformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                    )
                val bucket = "bucket"
                val key = UUID.randomUUID().toString()
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
                newVariant.assetId shouldBe persisted.id
                newVariant.apply {
                    this.attributes.height shouldBe attributes.height
                    this.attributes.width shouldBe attributes.width
                    this.attributes.format shouldBe attributes.format
                    this.transformation shouldBe variantTransformation
                    this.objectStoreBucket shouldBe bucket
                    this.objectStoreKey shouldBe key
                    this.isOriginalVariant shouldBe false
                }

                val assetData =
                    repository.fetchByPath(
                        persisted.path,
                        persisted.entryId,
                        null,
                        OrderBy.CREATED,
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
                                    height = 100,
                                    width = 100,
                                    format = ImageFormat.PNG,
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
                        width = 50,
                        height = 100,
                        format = ImageFormat.PNG,
                    )

                val transformation =
                    Transformation(
                        height = 50,
                        width = 100,
                        format = ImageFormat.PNG,
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

                shouldThrow<IllegalArgumentException> {
                    repository.storeNewVariant(pendingVariant)
                }
            }
    }

    @Nested
    inner class FetchByPathTests {
        @Test
        fun `fetching asset that does not exist returns null`() =
            runTest {
                repository.fetchByPath("/doesNotExist", null, null, OrderBy.CREATED) shouldBe null
            }

        @Test
        fun `fetching asset with entryId that does not exist returns null`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.fetchByPath(persisted.path, persisted.entryId!! + 1, null, OrderBy.CREATED) shouldBe null
            }

        @Test
        fun `returns an existing asset`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                val ready = persisted.markReady(LocalDateTime.now())
                repository.markReady(ready)
                val fetched = repository.fetchByPath(persisted.path, persisted.entryId, null, OrderBy.CREATED)

                assertFetchedAgainstAggregate(fetched, ready, true)
            }

        @Test
        fun `returns asset with path that has trailing slash`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val fetched =
                    repository.fetchByPath(
                        persisted.path + "/",
                        persisted.entryId,
                        null,
                        OrderBy.CREATED,
                    )

                fetched?.id shouldBe persisted.id
            }

        @Test
        fun `returns last created asset if multiple exist`() =
            runTest {
                val pending1 = createPendingAsset()
                val pending2 = createPendingAsset()
                repository.storeNew(pending1)
                val persisted2 = repository.storeNew(pending2)
                repository.markReady(persisted2.markReady(LocalDateTime.now()))

                repository.fetchByPath(pending1.path, entryId = null, transformation = null, OrderBy.CREATED)?.id shouldBe persisted2.id
            }

        @Test
        fun `returns an existing asset by entryId`() =
            runTest {
                val pending1 = createPendingAsset()
                val pending2 = createPendingAsset()
                val persisted1 = repository.storeNew(pending1)
                repository.markReady(persisted1.markReady(LocalDateTime.now()))
                val persisted2 = repository.storeNew(pending2)
                repository.markReady(persisted2.markReady(LocalDateTime.now()))

                repository.fetchByPath(persisted1.path, entryId = persisted1.entryId!!, transformation = null, OrderBy.CREATED)?.id shouldBe
                    persisted1.id
                repository.fetchByPath(persisted2.path, entryId = persisted2.entryId!!, transformation = null, OrderBy.CREATED)?.id shouldBe
                    persisted2.id
            }

        @Test
        fun `returns null if there is no asset in path at specific entryId`() =
            runTest {
                val pending = createPendingAsset()
                repository.storeNew(pending)
                repository.fetchByPath(pending.path, entryId = 1, transformation = null, OrderBy.CREATED) shouldBe null
            }

        @Test
        fun `returns existing variant based on transformation`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                    )
                val variant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(variant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset?.id shouldBe persisted.id
                fetchedAsset!!.variants shouldHaveSize 1
                assertFetchedVariantAgainstAggregate(fetchedAsset.variants.first(), persistedVariant)
            }

        @ParameterizedTest
        @MethodSource("io.direkt.infrastructure.datastore.AssetRepositoryTestDataProviders#variantTransformationSource")
        fun `fit is respected when fetching a variant`(transformation: Transformation) =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val variantTransformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = variantTransformation,
                    )
                val persistedVariant =
                    repository.storeNewVariant(pendingVariant)
                persistedVariant.assetId shouldBe persisted.id

                val assetData =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                assetData shouldNotBe null
                assetData!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch original variant with matching transformation`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
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
                    )

                val assetData =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = originalVariantTransformation,
                        orderBy = OrderBy.CREATED,
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
                val pending =
                    createPendingAsset(
                        labels =
                            mapOf(
                                "phone" to "iphone",
                                "hello" to "world",
                            ),
                    )
                val persisted = repository.storeNew(pending)

                val assetAndVariants =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = null,
                        transformation = null,
                        labels =
                            mapOf(
                                "phone" to "android",
                            ),
                    )
                assetAndVariants shouldBe null
            }

        @Test
        fun `returns asset at path matching all requested labels`() =
            runTest {
                val labels =
                    mapOf(
                        "phone" to "iphone",
                        "hello" to "world",
                    )
                val pending = createPendingAsset(labels = labels)
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                repository.storeNew(
                    createPendingAsset(
                        labels =
                            mapOf(
                                "phone" to "iphone",
                            ),
                    ),
                )

                val assetData =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = null,
                        transformation = null,
                        labels = labels,
                    )
                assetData shouldNotBe null
                assetData!!.id shouldBe persisted.id
                assetData.variants shouldHaveSize 1
                assetData.variants.first().apply {
                    isOriginalVariant shouldBe true
                }
                assetData.labels shouldContainExactly labels
            }

        @Test
        fun `returns asset at path matching some requested labels`() =
            runTest {
                val labels =
                    mapOf(
                        "phone" to "iphone",
                        "hello" to "world",
                    )
                val pending = createPendingAsset(labels = labels)
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val assetData =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = null,
                        transformation = null,
                        labels =
                            mapOf(
                                "phone" to "iphone",
                            ),
                    )
                assetData shouldNotBe null
                assetData!!.id shouldBe persisted.id
                assetData.variants shouldHaveSize 1
                assetData.variants.first().apply {
                    isOriginalVariant shouldBe true
                }
                assetData.labels shouldContainExactly labels
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
                repository.markReady(persisted1.markReady(LocalDateTime.now()))
                val pending2 = createPendingAsset(labels = labels)
                val persisted2 = repository.storeNew(pending2)
                repository.markReady(persisted2.markReady(LocalDateTime.now()))
                val ready1 =
                    persisted1.markReady(LocalDateTime.now()).update(
                        alt = "I'm updated!!",
                        tags = persisted1.tags,
                        labels = persisted1.labels,
                    )
                val updated1 = repository.update(ready1)
                repository
                    .fetchByPath(
                        path = updated1.path,
                        entryId = null,
                        transformation = null,
                        orderBy = OrderBy.MODIFIED,
                    )?.id shouldBe updated1.id
                repository
                    .fetchByPath(
                        path = persisted1.path,
                        entryId = null,
                        transformation = null,
                        orderBy = OrderBy.CREATED,
                    )?.id shouldBe persisted2.id
            }
    }

    @Nested
    inner class FetchAllByPathTests {
        @Test
        fun `returns asset at path`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                val ready = persisted.markReady(LocalDateTime.now())
                repository.markReady(ready)

                repository.fetchAllByPath("/users/123", null, limit = 1).apply {
                    this shouldHaveSize 1
                    assertFetchedAgainstAggregate(first(), ready, true)
                }
            }

        @Test
        fun `returns all assets at path`() =
            runTest {
                val pending1 = createPendingAsset()
                val pending2 = createPendingAsset()
                val persisted1 = repository.storeNew(pending1)
                repository.markReady(persisted1.markReady(LocalDateTime.now()))
                val persisted2 = repository.storeNew(pending2)
                repository.markReady(persisted2.markReady(LocalDateTime.now()))

                repository.fetchAllByPath(pending1.path, null, limit = 10).also {
                    it shouldHaveSize 2
                    it[0].id shouldBe persisted2.id
                    it[1].id shouldBe persisted1.id
                }
            }

        @Test
        fun `returns all assets at path ordered correctly`() =
            runTest {
                val pending1 = createPendingAsset()
                val pending2 = createPendingAsset()
                val persisted1 = repository.storeNew(pending1)
                repository.markReady(persisted1.markReady(LocalDateTime.now()))
                val persisted2 = repository.storeNew(pending2)
                repository.markReady(persisted2.markReady(LocalDateTime.now()))
                val ready1 =
                    persisted1.markReady(LocalDateTime.now()).update(
                        alt = "I'm updated!!",
                        tags = persisted1.tags,
                        labels = persisted1.labels,
                    )
                repository.update(ready1)

                repository.fetchAllByPath("/users/123", null, orderBy = OrderBy.CREATED, limit = 10).also {
                    it shouldHaveSize 2
                    it[0].id shouldBe persisted2.id
                    it[1].id shouldBe persisted1.id
                }
                repository.fetchAllByPath("/users/123", null, orderBy = OrderBy.MODIFIED, limit = 10).also {
                    it shouldHaveSize 2
                    it[0].id shouldBe persisted1.id
                    it[1].id shouldBe persisted2.id
                }
            }

        @Test
        fun `returns no assets at path if none have requested labels`() =
            runTest {
                val pending1 = createPendingAsset(labels = emptyMap())
                val pending2 = createPendingAsset(labels = emptyMap())
                repository.storeNew(pending1)
                repository.storeNew(pending2)

                repository.fetchAllByPath(
                    path = pending1.path,
                    transformation = null,
                    labels =
                        mapOf(
                            "phone" to "iphone",
                            "hello" to "world",
                        ),
                    limit = 10,
                ) shouldBe emptyList()
            }

        @Test
        fun `returns assets at path matching all requested labels`() =
            runTest {
                val labels =
                    mapOf(
                        "phone" to "iphone",
                        "hello" to "world",
                    )
                val pending1 = createPendingAsset(labels = labels)
                val pending2 =
                    createPendingAsset(
                        labels =
                            mapOf(
                                "phone" to "iphone",
                            ),
                    )
                val persisted1 = repository.storeNew(pending1)
                repository.storeNew(pending2)

                repository
                    .fetchAllByPath(
                        path = "/users/123",
                        transformation = null,
                        labels = labels,
                        limit = 10,
                    ).forAll {
                        it.id shouldBe persisted1.id
                    }
            }

        @Test
        fun `returns assets at path matching some requested labels`() =
            runTest {
                val labels =
                    mapOf(
                        "phone" to "iphone",
                        "hello" to "world",
                    )
                val dto1 = createPendingAsset(labels = labels)
                val dto2 = createPendingAsset(labels = labels)
                val persisted1 = repository.storeNew(dto1)
                repository.markReady(persisted1.markReady(LocalDateTime.now()))
                val persisted2 = repository.storeNew(dto2)
                repository.markReady(persisted2.markReady(LocalDateTime.now()))

                repository
                    .fetchAllByPath(
                        path = "/users/123",
                        transformation = null,
                        labels =
                            mapOf(
                                "phone" to "iphone",
                            ),
                        limit = 10,
                    ).also {
                        it shouldHaveSize 2
                        it[0].id shouldBe persisted2.id
                        it[1].id shouldBe persisted1.id
                    }
            }

        @Test
        fun `returns all assets even if they do not have a requested variant`() =
            runTest {
                val count = 3
                repeat(count) {
                    val pending = createPendingAsset()
                    val persisted = repository.storeNew(pending)
                    repository.markReady(persisted.markReady(LocalDateTime.now()))
                    val pendingVariant =
                        createPendingVariant(
                            assetId = persisted.id,
                            transformation =
                                Transformation(
                                    height = 10,
                                    width = 10,
                                    format = ImageFormat.PNG,
                                ),
                        )

                    repository.storeNewVariant(pendingVariant)
                }
                val transformation =
                    Transformation(
                        width = 8,
                        height = 5,
                        format = ImageFormat.JPEG,
                        fit = Fit.FIT,
                    )

                val fetched = repository.fetchAllByPath("/users/123", transformation, limit = 10)
                fetched shouldHaveSize 3
                fetched.forAll {
                    it.variants shouldHaveSize 0
                }
            }

        @Test
        fun `returns all assets and all variants`() =
            runTest {
                val count = 3
                repeat(count) {
                    val pending = createPendingAsset()
                    val persisted = repository.storeNew(pending)
                    repository.markReady(persisted.markReady(LocalDateTime.now()))
                    val pendingVariant =
                        createPendingVariant(
                            assetId = persisted.id,
                            transformation =
                                Transformation(
                                    height = 10,
                                    width = 10,
                                    format = ImageFormat.PNG,
                                ),
                        )
                    repository.storeNewVariant(pendingVariant)
                }

                val fetched =
                    repository.fetchAllByPath(
                        "/users/123",
                        Transformation(
                            height = 10,
                            width = 10,
                            format = ImageFormat.PNG,
                        ),
                        limit = 10,
                    )
                fetched shouldHaveSize 3
                fetched.forAll {
                    it.variants shouldHaveSize 1
                    it.variants.first().apply {
                        transformation.height shouldBe 10
                        transformation.height shouldBe 10
                        isOriginalVariant shouldBe false
                    }
                }
            }

        @Test
        fun `returns all assets and the existing requested variant`() =
            runTest {
                val count = 3
                repeat(count) {
                    val pending = createPendingAsset()
                    val persisted = repository.storeNew(pending)
                    repository.markReady(persisted.markReady(LocalDateTime.now()))
                    val pendingVariant =
                        createPendingVariant(
                            assetId = persisted.id,
                            transformation =
                                Transformation(
                                    height = 10,
                                    width = 10,
                                    format = ImageFormat.PNG,
                                ),
                        )
                    repository.storeNewVariant(pendingVariant)
                }

                val fetched = repository.fetchAllByPath("/users/123", null, limit = 10)
                fetched shouldHaveSize 3
                fetched.forAll {
                    it.variants shouldHaveSize 2
                    it.variants.find { variant -> variant.isOriginalVariant } shouldNotBe null
                    it.variants.find { variant ->
                        variant.transformation.height == 10 && variant.transformation.width == 10
                    } shouldNotBe null
                }
            }

        @Test
        fun `returns empty list if no assets in path`() =
            runTest {
                repository.fetchAllByPath("/users/123", null, limit = 10) shouldBe emptyList()
            }

        @Test
        fun `limit is respected`() =
            runTest {
                repeat(10) {
                    repository.storeNew(createPendingAsset()).let {
                        repository.markReady(it.markReady(LocalDateTime.now()))
                    }
                }
                repository.fetchAllByPath(
                    path = "/users/123",
                    transformation = null,
                    limit = 5,
                ) shouldHaveSize 5
            }

        @Test
        fun `no limit is respected if negative`() =
            runTest {
                repeat(10) {
                    repository.storeNew(createPendingAsset()).let {
                        repository.markReady(it.markReady(LocalDateTime.now()))
                    }
                }
                repository.fetchAllByPath(
                    path = "/users/123",
                    transformation = null,
                    limit = -1,
                ) shouldHaveSize 10
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
                val ready =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                repository.deleteByPath(
                    path = "/users/123",
                    entryId = 0,
                )

                repository.fetchByPath(ready.path, ready.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchByPath(
                    "/users/123",
                    entryId = null,
                    transformation = null,
                    OrderBy.CREATED,
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
                val pending = createPendingAsset()
                val ready =
                    repository
                        .storeNew(pending)
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                shouldNotThrowAny {
                    repository.deleteByPath("/users/123", entryId = 1)
                }

                repository.fetchByPath(ready.path, ready.entryId, null, OrderBy.CREATED)?.id shouldBe
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
                val ready1 =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                val ready2 =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }

                repository.deleteAllByPath("/users/123", limit = 1)

                repository.fetchByPath(
                    path = ready1.path,
                    entryId = ready1.entryId,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
                ) shouldNotBe null
                repository.fetchByPath(
                    path = ready2.path,
                    entryId = ready2.entryId,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
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
                val ready1 =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                val ready2 =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }

                repository.deleteAllByPath("/users/123", limit = -1)

                repository.fetchByPath(ready1.path, ready1.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchByPath(ready2.path, ready2.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchAllByPath("/users/123", null, limit = 10) shouldBe emptyList()
            }

        @Test
        fun `orderBy is respected when deleting assets at path`() =
            runTest {
                val ready1 =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now())
                        .also {
                            repository.markReady(it)
                        }
                val ready2 =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now())
                        .also {
                            repository.markReady(it)
                        }
                val updated =
                    repository.update(
                        ready1.update(
                            alt = "updated",
                            labels = emptyMap(),
                            tags = emptySet(),
                        ),
                    )
                updated.modifiedAt shouldBeAfter ready1.modifiedAt

                repository.deleteAllByPath("/users/123", limit = 1, orderBy = OrderBy.MODIFIED)

                repository.fetchByPath(ready1.path, ready1.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchByPath(ready2.path, ready2.entryId, null, OrderBy.CREATED) shouldNotBe null
                repository.fetchAllByPath("/users/123", null, limit = 10) shouldHaveSize 1
            }

        @Test
        fun `deletes nothing if no assets have supplied labels`() =
            runTest {
                val ready =
                    repository
                        .storeNew(
                            createPendingAsset(
                                labels = mapOf("animal" to "cat"),
                            ),
                        ).markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }

                repository.deleteAllByPath("/users/123", labels = mapOf("animal" to "dog"), limit = 1)

                repository.fetchByPath(
                    path = ready.path,
                    entryId = ready.entryId,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
                ) shouldNotBe null
            }

        @Test
        fun `deletes asset if it contains a superset of supplied labels`() =
            runTest {
                val ready =
                    repository
                        .storeNew(
                            createPendingAsset(
                                labels = mapOf("animal" to "cat", "phone" to "iphone"),
                            ),
                        ).markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }

                repository.deleteAllByPath("/users/123", labels = mapOf("animal" to "cat"), limit = 1)

                repository.fetchByPath(
                    path = ready.path,
                    entryId = ready.entryId,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
                ) shouldBe null
            }

        @Test
        fun `deletes assets with supplied labels`() =
            runTest {
                val ready1 =
                    repository
                        .storeNew(
                            createPendingAsset(
                                labels = mapOf("animal" to "cat"),
                            ),
                        ).markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                val ready2 =
                    repository
                        .storeNew(createPendingAsset())
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }

                repository.deleteAllByPath("/users/123", labels = mapOf("animal" to "cat"), limit = 1)

                repository.fetchByPath(
                    path = ready1.path,
                    entryId = ready1.entryId,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
                ) shouldBe null
                repository.fetchByPath(
                    path = ready2.path,
                    entryId = ready2.entryId,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
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
                val ready1 =
                    repository
                        .storeNew(createPendingAsset(path = "users/123"))
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                val ready2 =
                    repository
                        .storeNew(createPendingAsset(path = "users/123"))
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                val ready3 =
                    repository
                        .storeNew(createPendingAsset(path = "users/123/profile"))
                        .markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }

                repository.deleteRecursivelyByPath("/users/123")

                repository.fetchByPath(ready1.path, ready1.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchByPath(ready2.path, ready2.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchByPath(ready3.path, ready3.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchAllByPath("/users/123", null, limit = -1) shouldBe emptyList()
                repository.fetchAllByPath("users/123/profile", null, limit = -1) shouldBe emptyList()
            }

        @Test
        fun `deletes assets recursively with supplied labels`() =
            runTest {
                val ready1 =
                    repository
                        .storeNew(
                            createPendingAsset(
                                path = "/users/123",
                                labels = mapOf("animal" to "cat"),
                            ),
                        ).markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                val ready2 =
                    repository
                        .storeNew(
                            createPendingAsset(
                                path = "/users/123",
                            ),
                        ).markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                val ready3 =
                    repository
                        .storeNew(
                            createPendingAsset(
                                labels = mapOf("animal" to "cat"),
                                path = "/users/123/photo",
                            ),
                        ).markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }
                val ready4 =
                    repository
                        .storeNew(
                            createPendingAsset(
                                path = "/users/123/photo",
                            ),
                        ).markReady(LocalDateTime.now())
                        .also { repository.markReady(it) }

                repository.deleteRecursivelyByPath("/users/123", labels = mapOf("animal" to "cat"))

                repository.fetchByPath(
                    path = ready1.path,
                    entryId = ready1.entryId,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
                ) shouldBe null
                repository.fetchByPath(
                    path = ready2.path,
                    entryId = ready2.entryId,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
                ) shouldNotBe null
                repository.fetchByPath(
                    path = ready3.path,
                    entryId = ready3.entryId,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
                ) shouldBe null
                repository.fetchByPath(
                    path = ready4.path,
                    entryId = ready4.entryId,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
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
     * These test the repository's ability to fetch a variant by a given transformation. Both a positive
     * and negative match should be tested for each new transformation component.
     */
    @Nested
    inner class FetchVariantByTransformationTests {
        @ParameterizedTest
        @EnumSource(value = Fit::class)
        fun `can fetch variant by height and width transformation`(fit: Fit) =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        fit = fit,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset shouldNotBe null
                fetchedAsset!!.variants shouldHaveSize 1
                fetchedAsset.variants.first().id shouldBe persistedVariant.id

                val noVariant =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation.copy(fit = Fit.entries.first { it != fit }),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @ParameterizedTest
        @EnumSource(value = ImageFormat::class)
        fun `can fetch variant by format transformation`(format: ImageFormat) =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = format,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation =
                            Transformation(
                                height = 10,
                                width = 10,
                                format = format,
                            ),
                    )

                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset shouldNotBe null
                fetchedAsset!!.variants shouldHaveSize 1
                fetchedAsset.variants.first().id shouldBe persistedVariant.id

                val noVariant =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation.copy(format = ImageFormat.entries.first { it != format }),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @ParameterizedTest
        @EnumSource(value = Rotate::class)
        fun `can fetch variant by rotation transformation`(rotate: Rotate) =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        rotate = rotate,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset shouldNotBe null
                fetchedAsset!!.variants shouldHaveSize 1
                fetchedAsset.variants.first().id shouldBe persistedVariant.id

                val noVariant =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation.copy(rotate = Rotate.entries.first { it != rotate }),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `can fetch variant by horizontal flip transformation`(horizontalFlip: Boolean) =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        horizontalFlip = horizontalFlip,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset shouldNotBe null
                fetchedAsset!!.variants shouldHaveSize 1
                fetchedAsset.variants.first().id shouldBe persistedVariant.id

                val noVariant =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation.copy(horizontalFlip = !horizontalFlip),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @ParameterizedTest
        @EnumSource(value = Filter::class)
        fun `can fetch variant by filter transformation`(filter: Filter) =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        filter = filter,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset shouldNotBe null
                fetchedAsset!!.variants shouldHaveSize 1
                fetchedAsset.variants.first().id shouldBe persistedVariant.id

                val noVariant =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation.copy(filter = Filter.entries.first { it != filter }),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @ParameterizedTest
        @EnumSource(value = Gravity::class)
        fun `can fetch variant by gravity transformation`(gravity: Gravity) =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        gravity = gravity,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset shouldNotBe null
                fetchedAsset!!.variants shouldHaveSize 1
                fetchedAsset.variants.first().id shouldBe persistedVariant.id

                val noVariant =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation.copy(gravity = Gravity.entries.first { it != gravity }),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch variant by quality transformation`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        quality = 10,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset shouldNotBe null
                fetchedAsset!!.variants shouldHaveSize 1
                fetchedAsset.variants.first().id shouldBe persistedVariant.id

                val noVariant =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation.copy(quality = 50),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch variant by blur transformation`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        blur = 10,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset shouldNotBe null
                fetchedAsset!!.variants shouldHaveSize 1
                fetchedAsset.variants.first().id shouldBe persistedVariant.id

                val noVariant =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation.copy(blur = 50),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch variant by pad transformation`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        pad = 10,
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset shouldNotBe null
                fetchedAsset!!.variants shouldHaveSize 1
                fetchedAsset.variants.first().id shouldBe persistedVariant.id

                val noVariant =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation.copy(pad = 50),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch variant by background transformation`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        background = listOf(255, 255, 255, 255),
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val fetchedAsset =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                fetchedAsset shouldNotBe null
                fetchedAsset!!.variants shouldHaveSize 1
                fetchedAsset.variants.first().id shouldBe persistedVariant.id

                val noVariant =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation.copy(background = listOf(240, 255, 255, 255)),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch variant by all transformations at once`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now()))
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        horizontalFlip = true,
                        rotate = Rotate.ONE_HUNDRED_EIGHTY,
                        fit = Fit.STRETCH,
                        filter = Filter.GREYSCALE,
                        gravity = Gravity.ENTROPY,
                        quality = 50,
                        pad = 10,
                        background = listOf(100, 50, 34, 100),
                    )
                val pendingVariant =
                    createPendingVariant(
                        assetId = persisted.id,
                        transformation = transformation,
                    )
                val persistedVariant = repository.storeNewVariant(pendingVariant)

                val assetData =
                    repository.fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        transformation = transformation,
                    )
                assetData?.id shouldBe persisted.id
                assetData!!.variants shouldHaveSize 1
                assetData.variants.first().id shouldBe persistedVariant.id
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
                        .markReady(uploadedAt = LocalDateTime.now())
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
                    labels shouldContainExactly updated.labels
                    tags shouldContainExactly updated.tags
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

                val uploadedAt = LocalDateTime.now()
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
                    persisted.markReady(uploadedAt = LocalDateTime.now()).let {
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
                        height = 400,
                        width = 400,
                    )

                val pendingVariant =
                    createPendingVariant(
                        assetId = pending.id,
                        transformation = transformation,
                    )

                val persistedVariant = repository.storeNewVariant(pendingVariant)
                persistedVariant.uploadedAt shouldBe null
                val uploadedAt = LocalDateTime.now()
                val readyVariant =
                    repository
                        .markUploaded(
                            variant = persistedVariant.markReady(uploadedAt),
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
    }
}
