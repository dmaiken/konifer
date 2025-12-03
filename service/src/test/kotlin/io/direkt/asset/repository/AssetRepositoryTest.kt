package io.direkt.asset.repository

import io.direkt.asset.context.OrderBy
import io.direkt.asset.handler.AssetSource
import io.direkt.asset.handler.dto.StoreAssetVariantDto
import io.direkt.asset.handler.dto.UpdateAssetDto
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.asset.store.PersistResult
import io.image.model.Attributes
import io.image.model.Filter
import io.image.model.Fit
import io.image.model.Gravity
import io.image.model.ImageFormat
import io.image.model.LQIPs
import io.image.model.Rotate
import io.image.model.Transformation
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

abstract class AssetRepositoryTest {
    abstract fun createRepository(): AssetRepository

    val repository = createRepository()

    @Nested
    inner class StoreAssetTests {
        @Test
        fun `can store and fetch an asset`() =
            runTest {
                val dto = createAssetDto("/users/123", source = AssetSource.URL, url = "https://localhost.com")
                val assetAndVariants = repository.store(dto)
                assetAndVariants.asset.apply {
                    path shouldBe "/users/123"
                    entryId shouldBe 0
                    labels shouldContainExactly dto.request.labels
                    tags shouldContainExactly dto.request.tags
                    source shouldBe dto.source
                    sourceUrl shouldBe dto.request.url
                    createdAt shouldBe modifiedAt
                }
                assetAndVariants.variants shouldHaveSize 1
                assetAndVariants.variants.first().apply {
                    attributes.height shouldBe dto.attributes.height
                    attributes.width shouldBe dto.attributes.width
                    this.attributes.format shouldBe dto.attributes.format
                    this.transformation.height shouldBe dto.attributes.height
                    this.transformation.width shouldBe dto.attributes.width
                    this.transformation.format shouldBe dto.attributes.format
                    this.transformation.fit shouldBe Fit.FIT
                    this.isOriginalVariant shouldBe true
                    this.lqip shouldBe LQIPs.NONE
                }
                val fetched = repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null, OrderBy.CREATED)

                fetched shouldBe assetAndVariants
            }

        @Test
        fun `can store and fetch an asset with null url`() =
            runTest {
                val dto = createAssetDto("/users/123", source = AssetSource.UPLOAD, url = null)
                val assetAndVariants = repository.store(dto)
                assetAndVariants.asset.apply {
                    path shouldBe "/users/123"
                    entryId shouldBe 0
                    labels shouldContainExactly dto.request.labels
                    tags shouldContainExactly dto.request.tags
                    source shouldBe dto.source
                    sourceUrl shouldBe null
                    createdAt shouldBe modifiedAt
                }
                val fetched = repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null, OrderBy.CREATED)

                fetched shouldBe assetAndVariants
            }

        @Test
        fun `can store and fetch an asset with trailing slash`() =
            runTest {
                val dto = createAssetDto("/users/123/")
                val assetAndVariants = repository.store(dto)
                assetAndVariants.asset.path shouldNotEndWith "/"
                val fetched = repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null, OrderBy.CREATED)

                fetched shouldBe assetAndVariants
                fetched shouldBe
                    repository.fetchByPath(assetAndVariants.asset.path + "/", assetAndVariants.asset.entryId, null, OrderBy.CREATED)
            }

        @Test
        fun `storing an asset on an existent tree path appends the asset and increments entryId`() =
            runTest {
                val dto1 = createAssetDto("/users/123")
                val dto2 = createAssetDto("/users/123")
                val assetAndVariant1 = repository.store(dto1)
                val assetAndVariant2 = repository.store(dto2)

                assetAndVariant1.asset.entryId shouldBe 0
                assetAndVariant2.asset.entryId shouldBe 1
            }

        @Test
        fun `entryId is always the next highest value`() =
            runTest {
                val dto1 = createAssetDto("/users/123")
                val dto2 = createAssetDto("/users/123")
                val assetAndVariants1 = repository.store(dto1)
                val assetAndVariants2 = repository.store(dto2)
                assetAndVariants1.asset.entryId shouldBe 0
                assetAndVariants2.asset.entryId shouldBe 1
                repository.deleteAssetByPath("/users/123")

                val dto3 = createAssetDto("/users/123")
                val assetAndVariants3 = repository.store(dto3)
                assetAndVariants3.asset.entryId shouldBe 1

                repository.deleteAssetByPath("/users/123", entryId = 0)
                val dto4 = createAssetDto("/users/123")
                val assetAndVariants4 = repository.store(dto4)
                assetAndVariants4.asset.entryId shouldBe 2
            }
    }

    @Nested
    inner class StoreVariantTests {
        @Test
        fun `can store and fetch a variant`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val persistedAssetAndVariants = repository.store(dto)

                val persistResult =
                    PersistResult(
                        bucket = "bucket",
                        key = "key",
                    )
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
                val newVariant =
                    repository.storeVariant(
                        StoreAssetVariantDto(
                            path = persistedAssetAndVariants.asset.path,
                            entryId = persistedAssetAndVariants.asset.entryId,
                            persistResult = persistResult,
                            attributes = attributes,
                            transformation = variantTransformation,
                            lqips = LQIPs.NONE,
                        ),
                    )
                newVariant.asset shouldBe persistedAssetAndVariants.asset
                newVariant.variants shouldHaveSize 1
                newVariant.variants.first().apply {
                    this.attributes.height shouldBe attributes.height
                    this.attributes.width shouldBe attributes.width
                    this.attributes.format shouldBe attributes.format
                    this.transformation.height shouldBe variantTransformation.height
                    this.transformation.width shouldBe variantTransformation.width
                    this.transformation.format shouldBe variantTransformation.format
                    this.transformation.fit shouldBe variantTransformation.fit
                    this.objectStoreBucket shouldBe persistResult.bucket
                    this.objectStoreKey shouldBe persistResult.key
                    objectStoreBucket shouldBe persistResult.bucket
                    objectStoreKey shouldBe persistResult.key
                    isOriginalVariant shouldBe false
                }

                val assetAndVariants =
                    repository.fetchByPath(
                        persistedAssetAndVariants.asset.path,
                        persistedAssetAndVariants.asset.entryId,
                        null,
                        OrderBy.CREATED,
                    )
                assetAndVariants shouldNotBe null
                assetAndVariants?.asset shouldBe persistedAssetAndVariants.asset
                assetAndVariants?.variants?.size shouldBe 2
            }

        @Test
        fun `cannot store a variant of an asset that does not exist`() =
            runTest {
                val persistResult =
                    PersistResult(
                        bucket = "bucket",
                        key = "key",
                    )
                val attributes =
                    Attributes(
                        width = 100,
                        height = 100,
                        format = ImageFormat.PNG,
                    )

                shouldThrow<IllegalArgumentException> {
                    repository.storeVariant(
                        StoreAssetVariantDto(
                            path = "io/direkt/path/does/not/exist",
                            entryId = 1,
                            persistResult = persistResult,
                            attributes = attributes,
                            transformation =
                                Transformation(
                                    height = 100,
                                    width = 100,
                                    format = ImageFormat.PNG,
                                ),
                            lqips = LQIPs.NONE,
                        ),
                    )
                }
            }

        @Test
        fun `cannot store a duplicate variant`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val persistedAssetAndVariants = repository.store(dto)

                val persistResult =
                    PersistResult(
                        bucket = "bucket",
                        key = "key",
                    )
                val attributes =
                    Attributes(
                        width = 50,
                        height = 100,
                        format = ImageFormat.PNG,
                    )

                val variantDto =
                    StoreAssetVariantDto(
                        path = persistedAssetAndVariants.asset.path,
                        entryId = persistedAssetAndVariants.asset.entryId,
                        persistResult = persistResult,
                        attributes = attributes,
                        transformation =
                            Transformation(
                                height = 50,
                                width = 100,
                                format = ImageFormat.PNG,
                            ),
                        lqips = LQIPs.NONE,
                    )
                val newVariant = repository.storeVariant(variantDto)

                newVariant.asset shouldBe persistedAssetAndVariants.asset
                newVariant.variants shouldHaveSize 1
                newVariant.variants.first { !it.isOriginalVariant }.apply {
                    attributes.height shouldBe attributes.height
                    attributes.width shouldBe attributes.width
                    attributes.format shouldBe attributes.format
                    objectStoreBucket shouldBe persistResult.bucket
                    objectStoreKey shouldBe persistResult.key
                    isOriginalVariant shouldBe false
                }

                shouldThrow<IllegalArgumentException> {
                    repository.storeVariant(variantDto)
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
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId + 1, null, OrderBy.CREATED) shouldBe null
            }

        @Test
        fun `returns an existing asset`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val fetched = repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null, OrderBy.CREATED)

                fetched shouldBe assetAndVariants
            }

        @Test
        fun `returns asset with path that has trailing slash`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val fetched =
                    repository.fetchByPath(
                        assetAndVariants.asset.path + "/",
                        assetAndVariants.asset.entryId,
                        null,
                        OrderBy.CREATED,
                    )

                fetched shouldBe assetAndVariants
            }

        @Test
        fun `returns last created asset if multiple exist`() =
            runTest {
                val dto1 = createAssetDto("/users/123")
                val dto2 = createAssetDto("/users/123")
                repository.store(dto1)
                val asset2 = repository.store(dto2)

                repository.fetchByPath("/users/123", entryId = null, transformation = null, OrderBy.CREATED) shouldBe asset2
            }

        @Test
        fun `returns an existing asset by entryId`() =
            runTest {
                val dto1 = createAssetDto("/users/123")
                val dto2 = createAssetDto("/users/123")
                val assetAndVariant1 = repository.store(dto1)
                val assetAndVariant2 = repository.store(dto2)

                repository.fetchByPath("/users/123", entryId = 0, transformation = null, OrderBy.CREATED) shouldBe assetAndVariant1
                repository.fetchByPath("/users/123", entryId = 1, transformation = null, OrderBy.CREATED) shouldBe assetAndVariant2
            }

        @Test
        fun `returns null if there is no asset in path at specific entryId`() =
            runTest {
                val dto = createAssetDto("/users/123")
                repository.store(dto)
                repository.fetchByPath("/users/123", entryId = 1, transformation = null, OrderBy.CREATED) shouldBe null
            }

        @Test
        fun `returns existing variant based on transformation`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                    )
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant
            }

        @ParameterizedTest
        @MethodSource("io.direkt.asset.repository.AssetRepositoryTestDataProviders#variantTransformationSource")
        fun `fit is respected when fetching a variant`(transformation: Transformation) =
            runTest {
                val dto = createAssetDto("/users/123")
                val persistedAssetAndVariants = repository.store(dto)

                val persistResult =
                    PersistResult(
                        bucket = "bucket",
                        key = "key",
                    )
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
                val newVariant =
                    repository.storeVariant(
                        StoreAssetVariantDto(
                            path = persistedAssetAndVariants.asset.path,
                            entryId = persistedAssetAndVariants.asset.entryId,
                            persistResult = persistResult,
                            attributes = attributes,
                            transformation = variantTransformation,
                            lqips = LQIPs.NONE,
                        ),
                    )
                newVariant.asset shouldBe persistedAssetAndVariants.asset
                newVariant.variants shouldHaveSize 1

                val assetAndVariants =
                    repository.fetchByPath(
                        path = persistedAssetAndVariants.asset.path,
                        entryId = persistedAssetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                assetAndVariants shouldNotBe null
                assetAndVariants!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch original variant with matching transformation`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val persistedAssetAndVariants = repository.store(dto)

                val originalVariantTransformation =
                    Transformation(
                        height = dto.attributes.height,
                        width = dto.attributes.width,
                        format = dto.attributes.format,
                        fit = Fit.FIT,
                    )

                val assetAndVariants =
                    repository.fetchByPath(
                        path = persistedAssetAndVariants.asset.path,
                        entryId = persistedAssetAndVariants.asset.entryId,
                        transformation = originalVariantTransformation,
                        orderBy = OrderBy.CREATED,
                    )
                assetAndVariants shouldNotBe null
                assetAndVariants!!.asset shouldBe persistedAssetAndVariants.asset
                assetAndVariants.variants shouldHaveSize 1
                assetAndVariants.variants.first().apply {
                    isOriginalVariant shouldBe true
                }
            }

        @Test
        fun `returns no asset at path if none have requested labels`() =
            runTest {
                val dto =
                    createAssetDto(
                        "/users/123",
                        labels =
                            mapOf(
                                "phone" to "iphone",
                                "hello" to "world",
                            ),
                    )
                val persistedAssetAndVariants = repository.store(dto)

                val assetAndVariants =
                    repository.fetchByPath(
                        path = persistedAssetAndVariants.asset.path,
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
                val dto = createAssetDto("/users/123", labels = labels)
                val persistedAssetAndVariants = repository.store(dto)
                repository.store(
                    createAssetDto(
                        "/users/123",
                        labels =
                            mapOf(
                                "phone" to "iphone",
                            ),
                    ),
                )

                val assetAndVariants =
                    repository.fetchByPath(
                        path = persistedAssetAndVariants.asset.path,
                        entryId = null,
                        transformation = null,
                        labels = labels,
                    )
                assetAndVariants shouldNotBe null
                assetAndVariants!!.asset shouldBe persistedAssetAndVariants.asset
                assetAndVariants.variants shouldHaveSize 1
                assetAndVariants.variants.first().apply {
                    isOriginalVariant shouldBe true
                }
                assetAndVariants.asset.labels shouldContainExactly labels
            }

        @Test
        fun `returns asset at path matching some requested labels`() =
            runTest {
                val labels =
                    mapOf(
                        "phone" to "iphone",
                        "hello" to "world",
                    )
                val dto = createAssetDto("/users/123", labels = labels)
                val persistedAssetAndVariants = repository.store(dto)

                val assetAndVariants =
                    repository.fetchByPath(
                        path = persistedAssetAndVariants.asset.path,
                        entryId = null,
                        transformation = null,
                        labels =
                            mapOf(
                                "phone" to "iphone",
                            ),
                    )
                assetAndVariants shouldNotBe null
                assetAndVariants!!.asset shouldBe persistedAssetAndVariants.asset
                assetAndVariants.variants shouldHaveSize 1
                assetAndVariants.variants.first().apply {
                    isOriginalVariant shouldBe true
                }
                assetAndVariants.asset.labels shouldContainExactly labels
            }

        @Test
        fun `returns assets ordered by modifiedAt if specified`() =
            runTest {
                // Test labels in case ordering doesn't work with joins for some reason
                val labels =
                    mapOf(
                        "phone" to "iphone",
                    )
                val dto1 = createAssetDto("/users/123", labels = labels)
                var persisted1 = repository.store(dto1)
                val dto2 = createAssetDto("/users/123", labels = labels)
                val persisted2 = repository.store(dto2)
                persisted1 =
                    repository.update(
                        UpdateAssetDto(
                            path = dto1.path,
                            entryId = persisted1.asset.entryId,
                            request =
                                StoreAssetRequest(
                                    alt = "I'm updated!!",
                                ),
                        ),
                    )
                repository.fetchByPath(
                    path = persisted1.asset.path,
                    entryId = null,
                    transformation = null,
                    orderBy = OrderBy.MODIFIED,
                ) shouldBe persisted1
                repository.fetchByPath(
                    path = persisted1.asset.path,
                    entryId = null,
                    transformation = null,
                    orderBy = OrderBy.CREATED,
                ) shouldBe persisted2
            }
    }

    @Nested
    inner class FetchAllByPathTests {
        @Test
        fun `returns asset at path`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariant = repository.store(dto)

                repository.fetchAllByPath("/users/123", null, limit = 1) shouldBe listOf(assetAndVariant)
            }

        @Test
        fun `returns all assets at path`() =
            runTest {
                val dto1 = createAssetDto("/users/123")
                val dto2 = createAssetDto("/users/123")
                val assetAndVariant1 = repository.store(dto1)
                val assetAndVariant2 = repository.store(dto2)

                repository.fetchAllByPath("/users/123", null, limit = 10) shouldBe listOf(assetAndVariant2, assetAndVariant1)
            }

        @Test
        fun `returns all assets at path ordered correctly`() =
            runTest {
                val dto1 = createAssetDto("/users/123")
                val dto2 = createAssetDto("/users/123")
                var assetAndVariant1 = repository.store(dto1)
                val assetAndVariant2 = repository.store(dto2)
                delay(1000)
                assetAndVariant1 =
                    repository.update(
                        UpdateAssetDto(
                            path = assetAndVariant1.asset.path,
                            entryId = assetAndVariant1.asset.entryId,
                            request = StoreAssetRequest(alt = "I'm updated!!"),
                        ),
                    )

                repository.fetchAllByPath("/users/123", null, orderBy = OrderBy.CREATED, limit = 10) shouldBe
                    listOf(assetAndVariant2, assetAndVariant1)
                repository.fetchAllByPath("/users/123", null, orderBy = OrderBy.MODIFIED, limit = 10) shouldBe
                    listOf(assetAndVariant1, assetAndVariant2)
            }

        @Test
        fun `returns no assets at path if none have requested labels`() =
            runTest {
                val dto1 = createAssetDto("/users/123", labels = emptyMap())
                val dto2 = createAssetDto("/users/123", labels = emptyMap())
                repository.store(dto1)
                repository.store(dto2)

                repository.fetchAllByPath(
                    path = "/users/123",
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
                val dto1 = createAssetDto("/users/123", labels = labels)
                val dto2 =
                    createAssetDto(
                        "/users/123",
                        labels =
                            mapOf(
                                "phone" to "iphone",
                            ),
                    )
                val assetAndVariant1 = repository.store(dto1)
                repository.store(dto2)

                repository.fetchAllByPath(
                    path = "/users/123",
                    transformation = null,
                    labels = labels,
                    limit = 10,
                ) shouldBe listOf(assetAndVariant1)
            }

        @Test
        fun `returns assets at path matching some requested labels`() =
            runTest {
                val labels =
                    mapOf(
                        "phone" to "iphone",
                        "hello" to "world",
                    )
                val dto1 = createAssetDto("/users/123", labels = labels)
                val dto2 = createAssetDto("/users/123", labels = labels)
                val assetAndVariant1 = repository.store(dto1)
                val assetAndVariant2 = repository.store(dto2)

                repository.fetchAllByPath(
                    path = "/users/123",
                    transformation = null,
                    labels =
                        mapOf(
                            "phone" to "iphone",
                        ),
                    limit = 10,
                ) shouldBe listOf(assetAndVariant2, assetAndVariant1)
            }

        @Test
        fun `returns all assets even if they do not have a requested variant`() =
            runTest {
                val count = 3
                repeat(count) {
                    val dto = createAssetDto("/users/123")
                    val persistedAssetAndVariants = repository.store(dto)

                    val persistResult =
                        PersistResult(
                            bucket = "bucket",
                            key = UUID.randomUUID().toString(),
                        )
                    val attributes =
                        Attributes(
                            width = 10,
                            height = 10,
                            format = ImageFormat.PNG,
                        )

                    repository.storeVariant(
                        StoreAssetVariantDto(
                            path = persistedAssetAndVariants.asset.path,
                            entryId = persistedAssetAndVariants.asset.entryId,
                            persistResult = persistResult,
                            attributes = attributes,
                            transformation =
                                Transformation(
                                    height = 10,
                                    width = 10,
                                    format = ImageFormat.PNG,
                                ),
                            lqips = LQIPs.NONE,
                        ),
                    )
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
                    val dto = createAssetDto("/users/123")
                    val persistedAssetAndVariants = repository.store(dto)

                    val persistResult =
                        PersistResult(
                            bucket = "bucket",
                            key = UUID.randomUUID().toString(),
                        )
                    val attributes =
                        Attributes(
                            width = 10,
                            height = 10,
                            format = ImageFormat.PNG,
                        )

                    repository.storeVariant(
                        StoreAssetVariantDto(
                            path = persistedAssetAndVariants.asset.path,
                            entryId = persistedAssetAndVariants.asset.entryId,
                            persistResult = persistResult,
                            attributes = attributes,
                            transformation =
                                Transformation(
                                    height = 10,
                                    width = 10,
                                    format = ImageFormat.PNG,
                                ),
                            lqips = LQIPs.NONE,
                        ),
                    )
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
                    val dto = createAssetDto("/users/123")
                    val persistedAssetAndVariants = repository.store(dto)

                    val persistResult =
                        PersistResult(
                            bucket = "bucket",
                            key = UUID.randomUUID().toString(),
                        )
                    val attributes =
                        Attributes(
                            width = 10,
                            height = 10,
                            format = ImageFormat.PNG,
                        )

                    repository.storeVariant(
                        StoreAssetVariantDto(
                            path = persistedAssetAndVariants.asset.path,
                            entryId = persistedAssetAndVariants.asset.entryId,
                            persistResult = persistResult,
                            attributes = attributes,
                            transformation =
                                Transformation(
                                    height = 10,
                                    width = 10,
                                    format = ImageFormat.PNG,
                                ),
                            lqips = LQIPs.NONE,
                        ),
                    )
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
                    repository.store(createAssetDto("/users/123"))
                }
                repository.fetchAllByPath(
                    path = "/users/123",
                    transformation = null,
                    limit = 5,
                ) shouldHaveSize 5
            }
    }

    @Nested
    inner class DeleteByPathTests {
        @Test
        fun `deleteAssetByPath deletes the asset`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                repository.deleteAssetByPath("/users/123")

                repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchByPath("/users/123", entryId = null, transformation = null, OrderBy.CREATED) shouldBe null
            }

        @Test
        fun `deleteAssetByPath returns does nothing if asset does not exist`() =
            runTest {
                shouldNotThrowAny {
                    repository.deleteAssetByPath("/users/123")
                }
            }

        @Test
        fun `deleteAssetByPath returns does nothing if asset does not exist at specific entryId`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                shouldNotThrowAny {
                    repository.deleteAssetByPath("/users/123", entryId = 1)
                }

                repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null, OrderBy.CREATED) shouldBe
                    assetAndVariants
                repository.fetchAllByPath("/users/123", null, limit = 10) shouldBe listOf(assetAndVariants)
            }

        @Test
        fun `deleteAssetsByPath deletes all assets at path`() =
            runTest {
                val dto1 = createAssetDto("/users/123")
                val dto2 = createAssetDto("/users/123")
                val assetAndVariant1 = repository.store(dto1)
                val assetAndVariant2 = repository.store(dto2)

                repository.deleteAssetsByPath("/users/123", recursive = false)

                repository.fetchByPath(assetAndVariant1.asset.path, assetAndVariant1.asset.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchByPath(assetAndVariant2.asset.path, assetAndVariant2.asset.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchAllByPath("/users/123", null, limit = 10) shouldBe emptyList()
            }

        @Test
        fun `deleteAssetsByPath deletes all assets at path and under if recursive delete`() =
            runTest {
                val dto1 = createAssetDto("/users/123")
                val dto2 = createAssetDto("/users/123")
                val dto3 = createAssetDto("/users/123/profile")
                val assetAndVariants1 = repository.store(dto1)
                val assetAndVariants2 = repository.store(dto2)
                val assetAndVariants3 = repository.store(dto3)

                repository.deleteAssetsByPath("/users/123", recursive = true)

                repository.fetchByPath(assetAndVariants1.asset.path, assetAndVariants1.asset.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchByPath(assetAndVariants2.asset.path, assetAndVariants2.asset.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchByPath(assetAndVariants3.asset.path, assetAndVariants3.asset.entryId, null, OrderBy.CREATED) shouldBe null
                repository.fetchAllByPath("/users/123", null, limit = 10) shouldBe emptyList()
                repository.fetchAllByPath("/users/123/profile", null, limit = 10) shouldBe emptyList()
            }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `deleteAssetsByPath does nothing if nothing exists at path`(recursive: Boolean) =
            runTest {
                shouldNotThrowAny {
                    repository.deleteAssetsByPath("/users/123", recursive)
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
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        fit = fit,
                    )
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant

                val noVariant =
                    repository.fetchByPath(
                        path = persistedVariant.asset.path,
                        entryId = persistedVariant.asset.entryId,
                        transformation = transformation.copy(fit = Fit.entries.first { it != fit }),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @ParameterizedTest
        @EnumSource(value = ImageFormat::class)
        fun `can fetch variant by format transformation`(format: ImageFormat) =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = format,
                    )
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = format,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant

                val noVariant =
                    repository.fetchByPath(
                        path = persistedVariant.asset.path,
                        entryId = persistedVariant.asset.entryId,
                        transformation = transformation.copy(format = ImageFormat.entries.first { it != format }),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @ParameterizedTest
        @EnumSource(value = Rotate::class)
        fun `can fetch variant by rotation transformation`(rotate: Rotate) =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        rotate = rotate,
                    )
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant

                val noVariant =
                    repository.fetchByPath(
                        path = persistedVariant.asset.path,
                        entryId = persistedVariant.asset.entryId,
                        transformation = transformation.copy(rotate = Rotate.entries.first { it != rotate }),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `can fetch variant by horizontal flip transformation`(horizontalFlip: Boolean) =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        horizontalFlip = horizontalFlip,
                    )
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant

                val noVariant =
                    repository.fetchByPath(
                        path = persistedVariant.asset.path,
                        entryId = persistedVariant.asset.entryId,
                        transformation = transformation.copy(horizontalFlip = !horizontalFlip),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @ParameterizedTest
        @EnumSource(value = Filter::class)
        fun `can fetch variant by filter transformation`(filter: Filter) =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        filter = filter,
                    )
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant

                val noVariant =
                    repository.fetchByPath(
                        path = persistedVariant.asset.path,
                        entryId = persistedVariant.asset.entryId,
                        transformation = transformation.copy(filter = Filter.entries.first { it != filter }),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @ParameterizedTest
        @EnumSource(value = Gravity::class)
        fun `can fetch variant by gravity transformation`(gravity: Gravity) =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        gravity = gravity,
                    )
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant

                val noVariant =
                    repository.fetchByPath(
                        path = persistedVariant.asset.path,
                        entryId = persistedVariant.asset.entryId,
                        transformation = transformation.copy(gravity = Gravity.entries.first { it != gravity }),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch variant by quality transformation`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        quality = 10,
                    )
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant

                val noVariant =
                    repository.fetchByPath(
                        path = persistedVariant.asset.path,
                        entryId = persistedVariant.asset.entryId,
                        transformation = transformation.copy(quality = 50),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch variant by pad transformation`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        pad = 10,
                    )
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant

                val noVariant =
                    repository.fetchByPath(
                        path = persistedVariant.asset.path,
                        entryId = persistedVariant.asset.entryId,
                        transformation = transformation.copy(pad = 50),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch variant by background transformation`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
                val transformation =
                    Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                        background = listOf(255, 255, 255, 255),
                    )
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant

                val noVariant =
                    repository.fetchByPath(
                        path = persistedVariant.asset.path,
                        entryId = persistedVariant.asset.entryId,
                        transformation = transformation.copy(background = listOf(240, 255, 255, 255)),
                    )
                noVariant shouldNotBe null
                noVariant!!.variants shouldHaveSize 0
            }

        @Test
        fun `can fetch variant by all transformations at once`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)
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
                val variant =
                    StoreAssetVariantDto(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        persistResult =
                            PersistResult(
                                key = UUID.randomUUID().toString(),
                                bucket = UUID.randomUUID().toString(),
                            ),
                        attributes =
                            Attributes(
                                width = 10,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                        transformation = transformation,
                        lqips = LQIPs.NONE,
                    )
                val persistedVariant = repository.storeVariant(variant)

                val fetchedVariant =
                    repository.fetchByPath(
                        path = assetAndVariants.asset.path,
                        entryId = assetAndVariants.asset.entryId,
                        transformation = transformation,
                    )
                fetchedVariant shouldBe persistedVariant
            }
    }

    @Nested
    inner class UpdateTests {
        @Test
        fun `can update attributes of asset`() =
            runTest {
                val dto = createAssetDto("/users/123")
                val assetAndVariants = repository.store(dto)

                val updateDto =
                    UpdateAssetDto(
                        path = dto.path,
                        entryId = assetAndVariants.asset.entryId,
                        request =
                            StoreAssetRequest(
                                alt = "updated alt",
                                labels =
                                    mapOf(
                                        "updated" to "updated-value",
                                        "updated-phone" to "updated-iphone",
                                    ),
                                tags = setOf("updated-tag1", "updated-tag2"),
                            ),
                    )
                val updated = repository.update(updateDto)

                updated.variants shouldBe assetAndVariants.variants
                updated.asset.apply {
                    alt shouldBe updateDto.request.alt
                    labels shouldContainExactly updateDto.request.labels
                    tags shouldContainExactly updateDto.request.tags
                    modifiedAt shouldBeAfter assetAndVariants.asset.modifiedAt
                }
            }

        @Test
        fun `throws if asset does not exist`() =
            runTest {
                val updateDto =
                    UpdateAssetDto(
                        path = "/does/not/exist",
                        entryId = 10L,
                        request =
                            StoreAssetRequest(
                                alt = "updated alt",
                                labels =
                                    mapOf(
                                        "updated" to "updated-value",
                                        "updated-phone" to "updated-iphone",
                                    ),
                                tags = setOf("updated-tag1", "updated-tag2"),
                            ),
                    )
                shouldThrow<IllegalStateException> {
                    repository.update(updateDto)
                }
            }
    }
}
