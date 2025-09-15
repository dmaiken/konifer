package asset.repository

import asset.handler.StoreAssetDto
import asset.model.StoreAssetRequest
import asset.store.PersistResult
import image.model.Attributes
import image.model.ImageFormat
import image.model.LQIPs
import image.model.RequestedImageTransformation
import image.model.Transformation
import io.asset.handler.StoreAssetVariantDto
import io.image.model.Fit
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotEndWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

abstract class AssetRepositoryTest {
    abstract fun createRepository(): AssetRepository

    val repository = createRepository()

    companion object {
        @JvmStatic
        fun requestVariantSource() =
            listOf(
                arguments(
                    named(
                        "only height",
                        RequestedImageTransformation(
                            height = 10,
                            width = null,
                            format = null,
                            fit = Fit.SCALE,
                        ),
                    ),
                ),
                arguments(
                    named(
                        "only width",
                        RequestedImageTransformation(
                            height = null,
                            width = 10,
                            format = null,
                            fit = Fit.SCALE,
                        ),
                    ),
                ),
                arguments(
                    named(
                        "height and width but only one matches",
                        RequestedImageTransformation(
                            height = 10,
                            width = 5,
                            format = null,
                            fit = Fit.SCALE,
                        ),
                    ),
                ),
                arguments(
                    named(
                        "height and width but only one matches with mimeType",
                        RequestedImageTransformation(
                            height = 10,
                            width = 5,
                            format = ImageFormat.PNG,
                            fit = Fit.SCALE,
                        ),
                    ),
                ),
                arguments(
                    named(
                        "exact match",
                        RequestedImageTransformation(
                            height = 10,
                            width = 10,
                            format = ImageFormat.PNG,
                            fit = Fit.SCALE,
                        ),
                    ),
                ),
            )
    }

    @Test
    fun `can store and fetch an asset`() =
        runTest {
            val dto = createAssetDto("/users/123")
            val assetAndVariants = repository.store(dto)
            val fetched = repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null)

            fetched shouldBe assetAndVariants
        }

    @Test
    fun `can store and fetch an asset with trailing slash`() =
        runTest {
            val dto = createAssetDto("/users/123/")
            val assetAndVariants = repository.store(dto)
            assetAndVariants.asset.path shouldNotEndWith "/"
            val fetched = repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null)

            fetched shouldBe assetAndVariants
            fetched shouldBe repository.fetchByPath(assetAndVariants.asset.path + "/", assetAndVariants.asset.entryId, null)
        }

    @Test
    fun `fetching asset that does not exist returns null`() =
        runTest {
            repository.fetchByPath("/doesNotExist", null, null) shouldBe null
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
    fun `fetchByPath returns an existing asset`() =
        runTest {
            val dto = createAssetDto("/users/123")
            val assetAndVariants = repository.store(dto)
            val fetched = repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null)

            fetched shouldBe assetAndVariants
        }

    @Test
    fun `fetchByPath returns asset with path that has trailing slash`() =
        runTest {
            val dto = createAssetDto("/users/123")
            val assetAndVariants = repository.store(dto)
            val fetched = repository.fetchByPath(assetAndVariants.asset.path + "/", assetAndVariants.asset.entryId, null)

            fetched shouldBe assetAndVariants
        }

    @Test
    fun `fetchByPath returns last created asset if multiple exist`() =
        runTest {
            val dto1 = createAssetDto("/users/123")
            val dto2 = createAssetDto("/users/123")
            repository.store(dto1)
            val asset2 = repository.store(dto2)

            repository.fetchByPath("/users/123", entryId = null, transformation = null) shouldBe asset2
        }

    @Test
    fun `fetchByPath returns an existing asset by entryId`() =
        runTest {
            val dto1 = createAssetDto("/users/123")
            val dto2 = createAssetDto("/users/123")
            val assetAndVariant1 = repository.store(dto1)
            val assetAndVariant2 = repository.store(dto2)

            repository.fetchByPath("/users/123", entryId = 0, transformation = null) shouldBe assetAndVariant1
            repository.fetchByPath("/users/123", entryId = 1, transformation = null) shouldBe assetAndVariant2
        }

    @Test
    fun `fetchByPath returns null if there is no asset in path`() =
        runTest {
            repository.fetchByPath("/users/123", entryId = null, transformation = null) shouldBe null
        }

    @Test
    fun `fetchByPath returns null if there is no asset in path at specific entryId`() =
        runTest {
            val dto = createAssetDto("/users/123")
            repository.store(dto)
            repository.fetchByPath("/users/123", entryId = 1, transformation = null) shouldBe null
        }

    @ParameterizedTest
    @MethodSource("requestVariantSource")
    fun `fetchByPath returns variant based on requested transformation`(requested: RequestedImageTransformation) =
        runTest {
            val dto = createAssetDto("/users/123")
            val assetAndVariants = repository.store(dto)
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
                            height = 10,
                            width = 10,
                            format = ImageFormat.PNG,
                        ),
                    transformation = Transformation(
                        height = 10,
                        width = 10,
                        format = ImageFormat.PNG,
                    ),
                    lqips = LQIPs.NONE,
                )
            val persistedVariant = repository.storeVariant(variant)

            val fetchedVariant =
                repository.fetchByPath(
                    path = assetAndVariants.asset.path,
                    entryId = assetAndVariants.asset.entryId,
                    transformation = requested,
                )
            fetchedVariant shouldBe persistedVariant
        }

    @Test
    fun `fetchByPath returns original variant if querying by format only`() = runTest {

    }

    @Test
    fun `fetchAllByPath returns asset at path`() =
        runTest {
            val dto = createAssetDto("/users/123")
            val assetAndVariant = repository.store(dto)

            repository.fetchAllByPath("/users/123", null) shouldBe listOf(assetAndVariant)
        }

    @Test
    fun `fetchAllByPath returns all assets at path`() =
        runTest {
            val dto1 = createAssetDto("/users/123")
            val dto2 = createAssetDto("/users/123")
            val assetAndVariant1 = repository.store(dto1)
            val assetAndVariant2 = repository.store(dto2)

            repository.fetchAllByPath("/users/123", null) shouldBe listOf(assetAndVariant2, assetAndVariant1)
        }

    @Test
    fun `fetchAllByPath returns all assets even if they do not have a requested variant`() =
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
                        transformation = Transformation(
                            height = 10,
                            width = 10,
                            format = ImageFormat.PNG,
                        ),
                        lqips = LQIPs.NONE,
                    ),
                )
            }
            val requestedImageTransformation =
                RequestedImageTransformation(
                    width = 8,
                    height = 5,
                    format = null,
                    fit = Fit.SCALE,
                )

            val fetched = repository.fetchAllByPath("/users/123", requestedImageTransformation)
            fetched shouldHaveSize 3
            fetched.forAll {
                it.variants shouldHaveSize 0
            }
        }

    @Test
    fun `fetchAllByPath returns all assets and all variants`() =
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
                        transformation = Transformation(
                            height = 10,
                            width = 10,
                            format = ImageFormat.PNG,
                        ),
                        lqips = LQIPs.NONE,
                    ),
                )
            }
            val requestedImageTransformation =
                RequestedImageTransformation(
                    width = 10,
                    height = null,
                    format = null,
                    fit = Fit.SCALE,
                )

            val fetched = repository.fetchAllByPath("/users/123", requestedImageTransformation)
            fetched shouldHaveSize 3
            fetched.forAll {
                it.variants shouldHaveSize 1
                it.variants.first().apply {
                    transformations.height shouldBe 10
                    transformations.height shouldBe 10
                    isOriginalVariant shouldBe false
                }
            }
        }

    @Test
    fun `fetchAllByPath returns all assets and the existing requested variant`() =
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
                        transformation = Transformation(
                            height = 10,
                            width = 10,
                            format = ImageFormat.PNG,
                        ),
                        lqips = LQIPs.NONE,
                    ),
                )
            }

            val fetched = repository.fetchAllByPath("/users/123", null)
            fetched shouldHaveSize 3
            fetched.forAll {
                it.variants shouldHaveSize 2
                it.variants.find { variant -> variant.isOriginalVariant } shouldNotBe null
                it.variants.find { variant -> variant.transformations.height == 10 && variant.transformations.width == 10 } shouldNotBe null
            }
        }

    @Test
    fun `fetchAllByPath returns empty list if no assets in path`() =
        runTest {
            repository.fetchAllByPath("/users/123", null) shouldBe emptyList()
        }

    @Test
    fun `deleteAssetByPath deletes the asset`() =
        runTest {
            val dto = createAssetDto("/users/123")
            val assetAndVariants = repository.store(dto)
            repository.deleteAssetByPath("/users/123")

            repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null) shouldBe null
            repository.fetchByPath("/users/123", entryId = null, transformation = null) shouldBe null
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

            repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null) shouldBe assetAndVariants
            repository.fetchAllByPath("/users/123", null) shouldBe listOf(assetAndVariants)
        }

    @Test
    fun `deleteAssetsByPath deletes all assets at path`() =
        runTest {
            val dto1 = createAssetDto("/users/123")
            val dto2 = createAssetDto("/users/123")
            val assetAndVariant1 = repository.store(dto1)
            val assetAndVariant2 = repository.store(dto2)

            repository.deleteAssetsByPath("/users/123", recursive = false)

            repository.fetchByPath(assetAndVariant1.asset.path, assetAndVariant1.asset.entryId, null) shouldBe null
            repository.fetchByPath(assetAndVariant2.asset.path, assetAndVariant2.asset.entryId, null) shouldBe null
            repository.fetchAllByPath("/users/123", null) shouldBe emptyList()
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

            repository.fetchByPath(assetAndVariants1.asset.path, assetAndVariants1.asset.entryId, null) shouldBe null
            repository.fetchByPath(assetAndVariants2.asset.path, assetAndVariants2.asset.entryId, null) shouldBe null
            repository.fetchByPath(assetAndVariants3.asset.path, assetAndVariants3.asset.entryId, null) shouldBe null
            repository.fetchAllByPath("/users/123", null) shouldBe emptyList()
            repository.fetchAllByPath("/users/123/profile", null) shouldBe emptyList()
        }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `deleteAssetsByPath does nothing if nothing exists at path`(recursive: Boolean) =
        runTest {
            shouldNotThrowAny {
                repository.deleteAssetsByPath("/users/123", recursive)
            }
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

            val newVariant =
                repository.storeVariant(
                    StoreAssetVariantDto(
                        path = persistedAssetAndVariants.asset.path,
                        entryId = persistedAssetAndVariants.asset.entryId,
                        persistResult = persistResult,
                        attributes = attributes,
                        transformation = Transformation(
                            height = 10,
                            width = 10,
                            format = ImageFormat.PNG,
                        ),
                        lqips = LQIPs.NONE,
                    ),
                )
            newVariant.asset shouldBe persistedAssetAndVariants.asset
            newVariant.variants shouldHaveSize 1
            newVariant.variants.first().apply {
                attributes.height shouldBe attributes.height
                attributes.width shouldBe attributes.width
                attributes.format shouldBe attributes.format
                objectStoreBucket shouldBe persistResult.bucket
                objectStoreKey shouldBe persistResult.key
                isOriginalVariant shouldBe false
            }

            val assetAndVariants =
                repository.fetchByPath(
                    persistedAssetAndVariants.asset.path,
                    persistedAssetAndVariants.asset.entryId,
                    null,
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
                        path = "path/does/not/exist",
                        entryId = 1,
                        persistResult = persistResult,
                        attributes = attributes,
                        transformation = Transformation(
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
                    transformation = Transformation(
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

    private fun createAssetDto(treePath: String): StoreAssetDto {
        return StoreAssetDto(
            mimeType = "image/png",
            path = treePath,
            request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                ),
            attributes =
                Attributes(
                    format = ImageFormat.PNG,
                    width = 100,
                    height = 100,
                ),
            persistResult =
                PersistResult(
                    key = UUID.randomUUID().toString(),
                    bucket = "bucket",
                ),
            lqips = LQIPs.NONE,
        )
    }
}
