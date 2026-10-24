package io.konifer.infrastructure.datastore

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.common.selector.Order
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.projection.AssetData
import io.konifer.domain.context.toLimit
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.transformation.MetadataTransformation
import io.konifer.domain.transformation.PaddingTransformation
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.TransformationNormalizer
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.transformation.toPaddingAmount
import io.konifer.domain.transformation.toQuality
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.TransformationLimitProperties
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantAlreadyExistsException
import io.konifer.domain.variant.VariantData
import io.konifer.domain.variant.VariantSelection
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.domain.variant.attribute.PixelAttributes
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
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit

abstract class AssetRepositoryTest {
    abstract fun createRepository(): AssetRepository

    val repository = createRepository()

    @ParameterizedTest
    @EnumSource(VariantSelection::class)
    fun `variant refresh uses resolved asset identity and only returns selected uploaded variants`(selection: VariantSelection) =
        runTest {
            val asset = storeReadyAsset()
            val other = storeReadyAsset()
            val original = asset.variants.single()
            repository.fetchVariantsForDelivery(asset.id, selection).single().id shouldBe original.id
            repository.fetchVariantsForDelivery(AssetId(), selection) shouldBe emptyList()
            val pending = repository.storeNew(createPendingAsset())
            repository.fetchVariantsForDelivery(pending.id, selection) shouldBe emptyList()

            val transformation = Transformation.fromAttributes(original.attributes).copy(width = 50.toDimension())
            val variant = repository.storeNewVariant(createPendingVariant(assetId = asset.id, transformation = transformation))
            repository.fetchVariantsForDelivery(asset.id, selection).map { it.id } shouldContainExactly listOf(original.id)
            val ready = variant.markReady(LocalDateTime.now(UTC))
            val uploaded = repository.markUploaded(ready, CacheProperties())
            assertFetchedVariantAgainstAggregate(uploaded, ready)
            val second = storeReadyVariant(asset, transformation.copy(width = 25.toDimension()))
            val fetched = repository.fetchVariantsForDelivery(asset.id, selection)
            when (selection) {
                VariantSelection.ALL -> {
                    fetched.map { it.id } shouldContainExactly listOf(second.id, ready.id, original.id)
                    assertFetchedVariantAgainstAggregate(fetched[0], second)
                    assertFetchedVariantAgainstAggregate(fetched[1], ready)
                }

                VariantSelection.ORIGINAL_ONLY -> {
                    fetched.single().id shouldBe original.id
                }
            }
            repository.fetchVariantsForDelivery(other.id, selection).single().id shouldBe other.variants.single().id
        }

    @Nested
    inner class FetchForDeliveryTests {
        @Test
        fun `missing paths and entry ids return null`() =
            runTest {
                val asset = storeReadyAsset()

                repository.fetchForDelivery("/missing", null, VariantSelection.ALL) shouldBe null
                repository.fetchForDelivery(asset.path, asset.entryId!! + 1, VariantSelection.ALL) shouldBe null
            }

        @Test
        fun `pending assets are excluded before selecting an entry`() =
            runTest {
                val ready = storeReadyAsset()
                val pending = repository.storeNew(createPendingAsset())

                repository.fetchForDelivery(ready.path, null, VariantSelection.ALL)?.id shouldBe ready.id
                repository.fetchForDelivery(pending.path, pending.entryId, VariantSelection.ALL) shouldBe null
            }

        @Test
        fun `delivery fields match the persisted ready asset`() =
            runTest {
                val ready = storeReadyAsset(createPendingAsset(lqips = LQIPs("blurhash", "thumbhash")))
                val fetched = checkNotNull(repository.fetchForDelivery(ready.path, ready.entryId, VariantSelection.ALL))

                fetched.id shouldBe ready.id
                fetched.path shouldBe ready.path
                fetched.entryId shouldBe ready.entryId
                fetched.alt shouldBe ready.alt?.value
                fetched.modifiedAt.truncatedTo(ChronoUnit.MILLIS) shouldBe ready.modifiedAt.truncatedTo(ChronoUnit.MILLIS)
                fetched.variants shouldHaveSize 1
                assertFetchedVariantAgainstAggregate(fetched.variants.single(), ready.variants.single())
            }

        @Test
        fun `variant selection excludes pending variants and can select only the original`() =
            runTest {
                val asset = storeReadyAsset()
                val original = asset.variants.single()
                val transformation = Transformation.fromAttributes(original.attributes).copy(width = 50.toDimension())
                val uploaded = storeReadyVariant(asset, transformation)
                repository.storeNewVariant(
                    createPendingVariant(assetId = asset.id, transformation = transformation.copy(width = 25.toDimension())),
                )

                val all = checkNotNull(repository.fetchForDelivery(asset.path, asset.entryId, VariantSelection.ALL))
                all.variants.map { it.id } shouldContainExactlyInAnyOrder listOf(original.id, uploaded.id)

                val originalOnly = checkNotNull(repository.fetchForDelivery(asset.path, asset.entryId, VariantSelection.ORIGINAL_ONLY))
                originalOnly.variants.map { it.id } shouldContainExactly listOf(original.id)
                assertFetchedVariantAgainstAggregate(all.variants.single { it.id == uploaded.id }, uploaded)
            }

        @Test
        fun `explicit entry selection overrides latest entry selection`() =
            runTest {
                val first = storeReadyAsset()
                val second = storeReadyAsset()

                repository.fetchForDelivery(first.path, first.entryId, VariantSelection.ALL)?.id shouldBe first.id
                repository.fetchForDelivery(first.path, null, VariantSelection.ALL)?.id shouldBe second.id
            }

        @Test
        fun `labels filter assets before selecting the latest entry`() =
            runTest {
                val first = storeReadyAsset(createPendingAsset(labels = mapOf("kind" to "selected", "group" to "one")))
                val second = storeReadyAsset(createPendingAsset(labels = mapOf("kind" to "selected", "group" to "two")))
                storeReadyAsset(createPendingAsset(labels = mapOf("kind" to "other")))

                repository.fetchForDelivery(first.path, null, VariantSelection.ALL, labels = mapOf("kind" to "selected"))?.id shouldBe
                    second.id
                repository
                    .fetchForDelivery(
                        first.path,
                        null,
                        VariantSelection.ALL,
                        labels = mapOf("kind" to "selected", "group" to "one"),
                    )?.id shouldBe
                    first.id
                repository.fetchForDelivery(first.path, null, VariantSelection.ALL, labels = mapOf("kind" to "missing")) shouldBe null
            }

        @Test
        fun `new and modified ordering select the appropriate ready asset`() =
            runTest {
                val first = storeReadyAsset()
                val second = storeReadyAsset()
                val firstData = checkNotNull(repository.fetchByPath(first.path, first.entryId, VariantSelection.ALL))
                repository.update(Asset.Ready.from(firstData.copy(alt = "updated", modifiedAt = second.modifiedAt.plusSeconds(1))))

                repository.fetchForDelivery(first.path, null, VariantSelection.ALL, order = Order.NEW)?.id shouldBe second.id
                val modified = checkNotNull(repository.fetchForDelivery(first.path, null, VariantSelection.ALL, order = Order.MODIFIED))
                modified.id shouldBe first.id
                modified.modifiedAt.truncatedTo(ChronoUnit.MILLIS) shouldBe second.modifiedAt.plusSeconds(1).truncatedTo(ChronoUnit.MILLIS)
            }
    }

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
                    this.specification shouldBe VariantSpecification.Original
                    this.isOriginalVariant shouldBe true
                    this.lqips shouldBe LQIPs.NONE
                }
                val fetched = repository.fetchByPath(pendingPersisted.path, pendingPersisted.entryId, VariantSelection.ALL, Order.NEW)

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
                val fetched = repository.fetchByPath(persisted.path, persisted.entryId, VariantSelection.ALL, Order.NEW)

                fetched?.id shouldBe persisted.id
            }

        @Test
        fun `can store and fetch an asset with trailing slash`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.markReady(persisted.markReady(LocalDateTime.now(UTC)))
                persisted.path shouldNotEndWith "/"
                val fetched = repository.fetchByPath(persisted.path, persisted.entryId, VariantSelection.ALL, Order.NEW)

                fetched?.id shouldBe persisted.id
                fetched!!.id shouldBe
                    repository.fetchByPath(persisted.path + "/", persisted.entryId, VariantSelection.ALL, Order.NEW)?.id
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
                    this.specification shouldBe VariantSpecification.Transformed(variantTransformation)
                    this.objectStoreBucket shouldBe bucket
                    this.objectStoreKey shouldBe key
                    this.expiresAt shouldBe null
                    this.isOriginalVariant shouldBe false
                }

                val assetData =
                    repository.fetchByPath(
                        persisted.path,
                        persisted.entryId,
                        VariantSelection.ALL,
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
                        VariantSelection.ALL,
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
                    this.specification shouldBe VariantSpecification.Transformed(transformation)
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
        @ParameterizedTest
        @EnumSource(VariantSelection::class)
        fun `returns only the selected uploaded variants`(selection: VariantSelection) =
            runTest {
                val asset = storeReadyAsset()
                val original = asset.variants.single()
                val transformation = Transformation.fromAttributes(original.attributes).copy(width = 50.toDimension())
                val uploaded = storeReadyVariant(asset, transformation)
                repository.storeNewVariant(
                    createPendingVariant(assetId = asset.id, transformation = transformation.copy(width = 25.toDimension())),
                )

                val fetched = checkNotNull(repository.fetchByPath(asset.path, asset.entryId, selection))
                val expected =
                    when (selection) {
                        VariantSelection.ALL -> listOf(original.id, uploaded.id)
                        VariantSelection.ORIGINAL_ONLY -> listOf(original.id)
                    }
                fetched.variants.map { it.id } shouldContainExactlyInAnyOrder expected
            }

        @Test
        fun `fetching asset that does not exist returns null`() =
            runTest {
                repository.fetchByPath("/doesNotExist", null, VariantSelection.ALL, Order.NEW) shouldBe null
            }

        @Test
        fun `fetching asset with entryId that does not exist returns null`() =
            runTest {
                val pending = createPendingAsset()
                val persisted = repository.storeNew(pending)
                repository.fetchByPath(persisted.path, persisted.entryId!! + 1, VariantSelection.ALL, Order.NEW) shouldBe null
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
                        selection = VariantSelection.ALL,
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
                val fetched = repository.fetchByPath(persisted.path, persisted.entryId, VariantSelection.ALL, Order.NEW)

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

                repository.fetchByPath(pending1.path, entryId = null, selection = VariantSelection.ALL, Order.NEW)?.id shouldBe
                    persisted2.id
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

                repository
                    .fetchByPath(
                        persisted1.path,
                        entryId = persisted1.entryId!!,
                        selection = VariantSelection.ALL,
                        Order.NEW,
                    )?.id shouldBe
                    persisted1.id
                repository
                    .fetchByPath(
                        persisted2.path,
                        entryId = persisted2.entryId!!,
                        selection = VariantSelection.ALL,
                        Order.NEW,
                    )?.id shouldBe
                    persisted2.id
            }

        @Test
        fun `returns null if there is no asset in path at specific entryId`() =
            runTest {
                val pending = createPendingAsset()
                repository.storeNew(pending)
                repository.fetchByPath(pending.path, entryId = 1, selection = VariantSelection.ALL, Order.NEW) shouldBe null
            }

        @Test
        fun `fetched original variant matches its equivalent transformation`() =
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
                        order = Order.NEW,
                    )
                assetData shouldNotBe null
                assetData!!.id shouldBe persisted.id
                val matching = assetData.matchingVariants(VariantSpecification.Transformed(originalVariantTransformation))
                matching shouldHaveSize 1
                matching.first().apply {
                    isOriginalVariant shouldBe true
                }
            }

        @ParameterizedTest
        @CsvSource("rgba,bgra", "rgb,bgr", "argb,rgba")
        fun `pixel originals are selectable by identity and matching transformation`(
            storedChannels: String,
            differentChannels: String,
        ) = runTest {
            val attributes =
                Attributes(
                    width = 100.toDimension(),
                    height = 80.toDimension(),
                    format = ImageFormat.PIXELS,
                    colorSpace = ColorSpace.SRGB,
                    pixels =
                        PixelAttributes(
                            channels = storedChannels.toPixelChannels(),
                            layout = PixelLayout.INTERLEAVED,
                            dataType = PixelDataType.UINT8,
                        ),
                )
            val persisted = repository.storeNew(createPendingAsset(attributes = attributes))
            repository.markReady(persisted.markReady(LocalDateTime.now(UTC)))
            val originalId = persisted.variants.single().id
            val transformation =
                TransformationNormalizer().normalize(
                    limits = TransformationLimitProperties(),
                    requested =
                        RequestedTransformation(
                            format = ImageFormat.PIXELS,
                            pixelChannels = storedChannels.toPixelChannels(),
                        ),
                    originalVariantAttributes = attributes,
                )
            val matching = VariantSpecification.Transformed(transformation)
            val different =
                VariantSpecification.Transformed(
                    transformation.copy(pixels = PixelTransformation(differentChannels.toPixelChannels())),
                )

            val original =
                repository
                    .fetchByPath(
                        path = persisted.path,
                        entryId = persisted.entryId,
                        selection = VariantSelection.ORIGINAL_ONLY,
                    )!!
                    .variants
                    .single()
            original.id shouldBe originalId
            original.specification shouldBe VariantSpecification.Original
            original.attributes shouldBe attributes
            original.isOriginalVariant shouldBe true
            val fetched = repository.fetchByPath(persisted.path, persisted.entryId)!!
            fetched
                .matchingVariants(matching)
                .single()
                .id shouldBe originalId
            fetched.matchingVariants(different) shouldBe emptyList()
            val collection = repository.fetchAllByPath(persisted.path, limit = (-1).toLimit()).single()
            collection
                .matchingVariants(matching)
                .single()
                .id shouldBe originalId
            collection.matchingVariants(different) shouldBe emptyList()
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
                    selection = VariantSelection.ALL,
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
                            selection = VariantSelection.ALL,
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
                        selection = VariantSelection.ALL,
                        order = Order.MODIFIED,
                    )?.id shouldBe updated1.id
                repository
                    .fetchByPath(
                        path = persisted1.path,
                        entryId = null,
                        selection = VariantSelection.ALL,
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
                    )
                fetchedAsset?.id shouldBe persisted.id
                fetchedAsset!!.variants.map { it.id } shouldContainExactly listOf(persisted.variants.single().id)
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

                repository.fetchAllByPath("/users/123", VariantSelection.ALL, limit = 1.toLimit()).apply {
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
                    .fetchAllByPath("/users/123", VariantSelection.ALL, order = Order.NEW, limit = 10.toLimit())
                    .map { it.id } shouldContainExactly listOf(second.id, first.id)
                repository
                    .fetchAllByPath("/users/123", VariantSelection.ALL, order = Order.MODIFIED, limit = 10.toLimit())
                    .map { it.id } shouldContainExactly listOf(first.id, second.id)
            }

        @Test
        fun `returns no assets at path if none have requested labels`() =
            runTest {
                val assets = storeReadyAssets(count = 2) { createPendingAsset(labels = emptyMap()) }

                repository.fetchAllByPath(
                    path = assets.first().path,
                    selection = VariantSelection.ALL,
                    labels =
                        mapOf(
                            "phone" to "iphone",
                            "hello" to "world",
                        ),
                    limit = 10.toLimit(),
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
                        selection = VariantSelection.ALL,
                        labels = requestedLabels,
                        limit = 10.toLimit(),
                    )
                fetched.map { it.id } shouldContainExactly matching.map { it.id }.reversed()
            }

        @Test
        fun `returns all assets even if they do not have a matching transformation`() =
            runTest {
                val assets = storeReadyAssets(count = 3)
                val transformation =
                    Transformation(
                        width = 8.toDimension(),
                        height = 5.toDimension(),
                        format = ImageFormat.JPEG,
                        fit = Fit.FIT,
                        colorSpace = ColorSpace.SRGB,
                    )

                val fetched = repository.fetchAllByPath("/users/123", limit = 10.toLimit())
                fetched shouldHaveSize assets.size
                fetched.forAll {
                    it.variants shouldHaveSize 1
                    it.matchingVariants(VariantSpecification.Transformed(transformation)) shouldBe emptyList()
                }
            }

        @ParameterizedTest
        @EnumSource(VariantSelection::class)
        fun `returns all assets with the selected variants`(selection: VariantSelection) =
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

                val fetched =
                    repository.fetchAllByPath(
                        "/users/123",
                        selection,
                        limit = 10.toLimit(),
                    )
                fetched shouldHaveSize assets.size
                fetched.forAll {
                    if (selection == VariantSelection.ORIGINAL_ONLY) {
                        it.variants.single().isOriginalVariant shouldBe true
                    } else {
                        it.variants shouldHaveSize 2
                        it.variants.any { variant -> variant.isOriginalVariant } shouldBe true
                        it.variants.any { variant -> variant.specification == VariantSpecification.Transformed(transformation) } shouldBe
                            true
                    }
                }
            }

        @Test
        fun `returns empty list if no assets in path`() =
            runTest {
                repository.fetchAllByPath("/users/123", VariantSelection.ALL, limit = 10.toLimit()) shouldBe emptyList()
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
                selection = VariantSelection.ALL,
                limit = limit.toLimit(),
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
                    selection = VariantSelection.ALL,
                    limit = 10.toLimit(),
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

                repository.fetchByPath(ready.path, ready.entryId, VariantSelection.ALL, Order.NEW) shouldBe null
                repository.fetchByPath(
                    "/users/123",
                    entryId = null,
                    selection = VariantSelection.ALL,
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

                repository.fetchByPath(ready.path, ready.entryId, VariantSelection.ALL, Order.NEW)?.id shouldBe
                    ready.id
                repository.fetchAllByPath("/users/123", VariantSelection.ALL, limit = 10.toLimit()).apply {
                    this shouldHaveSize 1
                    first().id shouldBe ready.id
                }
            }
    }

    @Test
    fun `entry IDs are never reused after deleting the highest entry or emptying a path`() =
        runTest {
            val first = storeReadyAsset()
            val highest = storeReadyAsset()
            repository.deleteByPath(highest.path, checkNotNull(highest.entryId))
            val replacement = storeReadyAsset()
            checkNotNull(replacement.entryId) shouldBe checkNotNull(highest.entryId) + 1
            repository.deleteByPath(highest.path, checkNotNull(highest.entryId))
            repository.fetchByPath(replacement.path, replacement.entryId)?.id shouldBe replacement.id

            repository.deleteAllByPath(first.path)
            val afterEmpty = storeReadyAsset()
            checkNotNull(afterEmpty.entryId) shouldBe checkNotNull(replacement.entryId) + 1
            repository.deleteByPath(replacement.path, checkNotNull(replacement.entryId))
            repository.fetchByPath(afterEmpty.path, afterEmpty.entryId)?.id shouldBe afterEmpty.id
        }

    @Nested
    inner class DeleteAllByPathTests {
        @Test
        fun `deletes all assets at path`() =
            runTest {
                val ready1 = storeReadyAsset()
                val ready2 = storeReadyAsset()

                repository.deleteAllByPath("/users/123")

                repository.fetchByPath(ready1.path, ready1.entryId, VariantSelection.ALL, Order.NEW) shouldBe null
                repository.fetchByPath(ready2.path, ready2.entryId, VariantSelection.ALL, Order.NEW) shouldBe null
                repository.fetchAllByPath("/users/123", VariantSelection.ALL, limit = 10.toLimit()) shouldBe emptyList()
            }

        @Test
        fun `deletes nothing if no assets have supplied labels`() =
            runTest {
                val ready = storeReadyAsset(createPendingAsset(labels = mapOf("animal" to "cat")))

                repository.deleteAllByPath("/users/123", labels = mapOf("animal" to "dog"))

                repository.fetchByPath(
                    path = ready.path,
                    entryId = ready.entryId,
                    selection = VariantSelection.ALL,
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

                repository.deleteAllByPath("/users/123", labels = mapOf("animal" to "cat"))

                repository.fetchByPath(
                    path = deleted.path,
                    entryId = deleted.entryId,
                    selection = VariantSelection.ALL,
                    order = Order.NEW,
                ) shouldBe null
                repository.fetchByPath(
                    path = retained.path,
                    entryId = retained.entryId,
                    selection = VariantSelection.ALL,
                    order = Order.NEW,
                ) shouldNotBe null
                repository.fetchAllByPath(
                    path = "/users/123",
                    selection = VariantSelection.ALL,
                    limit = 10.toLimit(),
                ) shouldHaveSize 1
            }

        @Test
        fun `does nothing if nothing exists at path`() =
            runTest {
                shouldNotThrowAny {
                    repository.deleteAllByPath(
                        path = "/users/123",
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

                repository.fetchByPath(ready1.path, ready1.entryId, VariantSelection.ALL, Order.NEW) shouldBe null
                repository.fetchByPath(ready2.path, ready2.entryId, VariantSelection.ALL, Order.NEW) shouldBe null
                repository.fetchByPath(ready3.path, ready3.entryId, VariantSelection.ALL, Order.NEW) shouldBe null
                repository.fetchAllByPath("/users/123", VariantSelection.ALL, limit = (-1).toLimit()) shouldBe emptyList()
                repository.fetchAllByPath("users/123/profile", VariantSelection.ALL, limit = (-1).toLimit()) shouldBe emptyList()
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
                    selection = VariantSelection.ALL,
                    order = Order.NEW,
                ) shouldBe null
                repository.fetchByPath(
                    path = ready2.path,
                    entryId = ready2.entryId,
                    selection = VariantSelection.ALL,
                    order = Order.NEW,
                ) shouldNotBe null
                repository.fetchByPath(
                    path = ready3.path,
                    entryId = ready3.entryId,
                    selection = VariantSelection.ALL,
                    order = Order.NEW,
                ) shouldBe null
                repository.fetchByPath(
                    path = ready4.path,
                    entryId = ready4.entryId,
                    selection = VariantSelection.ALL,
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
     * Verifies that persisted transformations retain the domain identity needed for matching in memory.
     */
    @Nested
    inner class StoredTransformationMatchingTests {
        @ParameterizedTest(name = "{0}")
        @MethodSource("io.konifer.infrastructure.datastore.AssetRepositoryTestDataProviders#transformationLookupSource")
        fun `fetched variants match only an equivalent transformation`(case: TransformationLookupCase) =
            runTest {
                val asset = storeReadyAsset()
                val variant = storeReadyVariant(asset, case.stored)

                val matching =
                    checkNotNull(
                        repository.fetchByPath(
                            path = asset.path,
                            entryId = asset.entryId,
                        ),
                    )
                matching.variants shouldHaveSize 2
                matching.matchingVariants(VariantSpecification.Transformed(case.stored)).map { it.id } shouldContainExactly
                    listOf(variant.id)
                matching.matchingVariants(VariantSpecification.Transformed(case.nonMatching)) shouldBe emptyList()
            }

        @Test
        fun `fetched variant retains all transformation parameters`() =
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
                        ),
                    )
                fetched.variants shouldHaveSize 2
                fetched.matchingVariants(VariantSpecification.Transformed(transformation)).map { it.id } shouldContainExactly
                    listOf(variant.id)
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
                            selection = VariantSelection.ALL,
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
                            selection = VariantSelection.ALL,
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
                            )
                        }
                readyVariant shouldNotBe null
                val matching = readyVariant!!.matchingVariants(VariantSpecification.Transformed(transformation))
                matching shouldHaveSize 1
                matching.first().specification shouldBe VariantSpecification.Transformed(transformation)
                matching
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
                        selection = VariantSelection.ALL,
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
                    )!!
                    .matchingVariants(VariantSpecification.Transformed(secondTransformation)) shouldHaveSize 0
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
                            selection = VariantSelection.ALL,
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
                            selection = VariantSelection.ALL,
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
                            selection = VariantSelection.ALL,
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
                            selection = VariantSelection.ALL,
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

    private fun AssetData.matchingVariants(specification: VariantSpecification): List<VariantData> =
        variants.filter { specification.matches(it.specification) { it.attributes } }

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
