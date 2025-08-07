package asset.repository

import asset.handler.StoreAssetDto
import asset.model.StoreAssetRequest
import asset.store.PersistResult
import image.model.ImageAttributes
import image.model.LQIPs
import image.model.RequestedImageAttributes
import io.asset.handler.StoreAssetVariantDto
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
                        RequestedImageAttributes(
                            height = 10,
                            width = null,
                            mimeType = null,
                        ),
                    ),
                ),
                arguments(
                    named(
                        "only width",
                        RequestedImageAttributes(
                            height = null,
                            width = 10,
                            mimeType = null,
                        ),
                    ),
                ),
                arguments(
                    named(
                        "height and width but only one matches",
                        RequestedImageAttributes(
                            height = 10,
                            width = 5,
                            mimeType = null,
                        ),
                    ),
                ),
                arguments(
                    named(
                        "height and width but only one matches with mimeType",
                        RequestedImageAttributes(
                            height = 10,
                            width = 5,
                            mimeType = "image/png",
                        ),
                    ),
                ),
                arguments(
                    named(
                        "exact match",
                        RequestedImageAttributes(
                            height = 10,
                            width = 10,
                            mimeType = "image/png",
                        ),
                    ),
                ),
            )
    }

    @Test
    fun `can store and fetch an asset`() =
        runTest {
            val dto = createAssetDto("root.users.123")
            val assetAndVariants = repository.store(dto)
            val fetched = repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null)

            fetched shouldBe assetAndVariants
        }

    @Test
    fun `fetching asset that does not exist returns null`() =
        runTest {
            repository.fetchByPath("root.doesNotExist", null, null) shouldBe null
        }

    @Test
    fun `storing an asset on an existent tree path appends the asset and increments entryId`() =
        runTest {
            val dto1 = createAssetDto("root.users.123")
            val dto2 = createAssetDto("root.users.123")
            val assetAndVariant1 = repository.store(dto1)
            val assetAndVariant2 = repository.store(dto2)

            assetAndVariant1.asset.entryId shouldBe 0
            assetAndVariant2.asset.entryId shouldBe 1
        }

    @Test
    fun `fetchByPath returns an existing asset`() =
        runTest {
            val dto = createAssetDto("root.users.123")
            val assetAndVariants = repository.store(dto)
            val fetched = repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null)

            fetched shouldBe assetAndVariants
        }

    @Test
    fun `fetchByPath returns last created asset if multiple exist`() =
        runTest {
            val dto1 = createAssetDto("root.users.123")
            val dto2 = createAssetDto("root.users.123")
            repository.store(dto1)
            val asset2 = repository.store(dto2)

            repository.fetchByPath("root.users.123", entryId = null, requestedImageAttributes = null) shouldBe asset2
        }

    @Test
    fun `fetchByPath returns an existing asset by entryId`() =
        runTest {
            val dto1 = createAssetDto("root.users.123")
            val dto2 = createAssetDto("root.users.123")
            val assetAndVariant1 = repository.store(dto1)
            val assetAndVariant2 = repository.store(dto2)

            repository.fetchByPath("root.users.123", entryId = 0, requestedImageAttributes = null) shouldBe assetAndVariant1
            repository.fetchByPath("root.users.123", entryId = 1, requestedImageAttributes = null) shouldBe assetAndVariant2
        }

    @Test
    fun `fetchByPath returns null if there is no asset in path`() =
        runTest {
            repository.fetchByPath("root.users.123", entryId = null, requestedImageAttributes = null) shouldBe null
        }

    @Test
    fun `fetchByPath returns null if there is no asset in path at specific entryId`() =
        runTest {
            val dto = createAssetDto("root.users.123")
            repository.store(dto)
            repository.fetchByPath("root.users.123", entryId = 1, requestedImageAttributes = null) shouldBe null
        }

    @ParameterizedTest
    @MethodSource("requestVariantSource")
    fun `fetchByPath returns variant based on requested attributes`(requested: RequestedImageAttributes) =
        runTest {
            val dto = createAssetDto("root.users.123")
            val assetAndVariants = repository.store(dto)
            val variant =
                StoreAssetVariantDto(
                    treePath = assetAndVariants.asset.path,
                    entryId = assetAndVariants.asset.entryId,
                    persistResult =
                        PersistResult(
                            key = UUID.randomUUID().toString(),
                            bucket = UUID.randomUUID().toString(),
                        ),
                    imageAttributes =
                        ImageAttributes(
                            height = 10,
                            width = 10,
                            mimeType = "image/png",
                        ),
                    lqips = LQIPs.NONE,
                )
            val persistedVariant = repository.storeVariant(variant)

            val fetchedVariant =
                repository.fetchByPath(
                    treePath = assetAndVariants.asset.path,
                    entryId = assetAndVariants.asset.entryId,
                    requestedImageAttributes = requested,
                )
            fetchedVariant shouldBe persistedVariant
        }

    @Test
    fun `fetchAllByPath returns asset at path`() =
        runTest {
            val dto = createAssetDto("root.users.123")
            val assetAndVariant = repository.store(dto)

            repository.fetchAllByPath("root.users.123", null) shouldBe listOf(assetAndVariant)
        }

    @Test
    fun `fetchAllByPath returns all assets at path`() =
        runTest {
            val dto1 = createAssetDto("root.users.123")
            val dto2 = createAssetDto("root.users.123")
            val assetAndVariant1 = repository.store(dto1)
            val assetAndVariant2 = repository.store(dto2)

            repository.fetchAllByPath("root.users.123", null) shouldBe listOf(assetAndVariant2, assetAndVariant1)
        }

    @Test
    fun `fetchAllByPath returns all assets even if they do not have a requested variant`() =
        runTest {
            val count = 3
            repeat(count) {
                val dto = createAssetDto("root.users.123")
                val persistedAssetAndVariants = repository.store(dto)

                val persistResult =
                    PersistResult(
                        bucket = "bucket",
                        key = UUID.randomUUID().toString(),
                    )
                val imageAttributes =
                    ImageAttributes(
                        width = 10,
                        height = 10,
                        mimeType = "image/png",
                    )

                repository.storeVariant(
                    StoreAssetVariantDto(
                        treePath = persistedAssetAndVariants.asset.path,
                        entryId = persistedAssetAndVariants.asset.entryId,
                        persistResult = persistResult,
                        imageAttributes = imageAttributes,
                        lqips = LQIPs.NONE,
                    ),
                )
            }
            val requestedImageAttributes =
                RequestedImageAttributes(
                    width = 8,
                    height = 5,
                    mimeType = null,
                )

            val fetched = repository.fetchAllByPath("root.users.123", requestedImageAttributes)
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
                val dto = createAssetDto("root.users.123")
                val persistedAssetAndVariants = repository.store(dto)

                val persistResult =
                    PersistResult(
                        bucket = "bucket",
                        key = UUID.randomUUID().toString(),
                    )
                val imageAttributes =
                    ImageAttributes(
                        width = 10,
                        height = 10,
                        mimeType = "image/png",
                    )

                repository.storeVariant(
                    StoreAssetVariantDto(
                        treePath = persistedAssetAndVariants.asset.path,
                        entryId = persistedAssetAndVariants.asset.entryId,
                        persistResult = persistResult,
                        imageAttributes = imageAttributes,
                        lqips = LQIPs.NONE,
                    ),
                )
            }
            val requestedImageAttributes =
                RequestedImageAttributes(
                    width = 10,
                    height = null,
                    mimeType = null,
                )

            val fetched = repository.fetchAllByPath("root.users.123", requestedImageAttributes)
            fetched shouldHaveSize 3
            fetched.forAll {
                it.variants shouldHaveSize 1
                it.variants.first().apply {
                    attributes.height shouldBe 10
                    attributes.height shouldBe 10
                    isOriginalVariant shouldBe false
                }
            }
        }

    @Test
    fun `fetchAllByPath returns all assets and the existing requested variant`() =
        runTest {
            val count = 3
            repeat(count) {
                val dto = createAssetDto("root.users.123")
                val persistedAssetAndVariants = repository.store(dto)

                val persistResult =
                    PersistResult(
                        bucket = "bucket",
                        key = UUID.randomUUID().toString(),
                    )
                val imageAttributes =
                    ImageAttributes(
                        width = 10,
                        height = 10,
                        mimeType = "image/png",
                    )

                repository.storeVariant(
                    StoreAssetVariantDto(
                        treePath = persistedAssetAndVariants.asset.path,
                        entryId = persistedAssetAndVariants.asset.entryId,
                        persistResult = persistResult,
                        imageAttributes = imageAttributes,
                        lqips = LQIPs.NONE,
                    ),
                )
            }

            val fetched = repository.fetchAllByPath("root.users.123", null)
            fetched shouldHaveSize 3
            fetched.forAll {
                it.variants shouldHaveSize 2
                it.variants.find { variant -> variant.isOriginalVariant } shouldNotBe null
                it.variants.find { variant -> variant.attributes.height == 10 && variant.attributes.width == 10 } shouldNotBe null
            }
        }

    @Test
    fun `fetchAllByPath returns empty list if no assets in path`() =
        runTest {
            repository.fetchAllByPath("root.users.123", null) shouldBe emptyList()
        }

    @Test
    fun `deleteAssetByPath deletes the asset`() =
        runTest {
            val dto = createAssetDto("root.users.123")
            val assetAndVariants = repository.store(dto)
            repository.deleteAssetByPath("root.users.123")

            repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null) shouldBe null
            repository.fetchByPath("root.users.123", entryId = null, requestedImageAttributes = null) shouldBe null
        }

    @Test
    fun `deleteAssetByPath returns does nothing if asset does not exist`() =
        runTest {
            shouldNotThrowAny {
                repository.deleteAssetByPath("root.users.123")
            }
        }

    @Test
    fun `deleteAssetByPath returns does nothing if asset does not exist at specific entryId`() =
        runTest {
            val dto = createAssetDto("root.users.123")
            val assetAndVariants = repository.store(dto)
            shouldNotThrowAny {
                repository.deleteAssetByPath("root.users.123", entryId = 1)
            }

            repository.fetchByPath(assetAndVariants.asset.path, assetAndVariants.asset.entryId, null) shouldBe assetAndVariants
            repository.fetchAllByPath("root.users.123", null) shouldBe listOf(assetAndVariants)
        }

    @Test
    fun `deleteAssetsByPath deletes all assets at path`() =
        runTest {
            val dto1 = createAssetDto("root.users.123")
            val dto2 = createAssetDto("root.users.123")
            val assetAndVariant1 = repository.store(dto1)
            val assetAndVariant2 = repository.store(dto2)

            repository.deleteAssetsByPath("root.users.123", recursive = false)

            repository.fetchByPath(assetAndVariant1.asset.path, assetAndVariant1.asset.entryId, null) shouldBe null
            repository.fetchByPath(assetAndVariant2.asset.path, assetAndVariant2.asset.entryId, null) shouldBe null
            repository.fetchAllByPath("root.users.123", null) shouldBe emptyList()
        }

    @Test
    fun `deleteAssetsByPath deletes all assets at path and under if recursive delete`() =
        runTest {
            val dto1 = createAssetDto("root.users.123")
            val dto2 = createAssetDto("root.users.123")
            val dto3 = createAssetDto("root.users.123.profile")
            val assetAndVariants1 = repository.store(dto1)
            val assetAndVariants2 = repository.store(dto2)
            val assetAndVariants3 = repository.store(dto3)

            repository.deleteAssetsByPath("root.users.123", recursive = true)

            repository.fetchByPath(assetAndVariants1.asset.path, assetAndVariants1.asset.entryId, null) shouldBe null
            repository.fetchByPath(assetAndVariants2.asset.path, assetAndVariants2.asset.entryId, null) shouldBe null
            repository.fetchByPath(assetAndVariants3.asset.path, assetAndVariants3.asset.entryId, null) shouldBe null
            repository.fetchAllByPath("root.users.123", null) shouldBe emptyList()
            repository.fetchAllByPath("root.users.123.profile", null) shouldBe emptyList()
        }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `deleteAssetsByPath does nothing if nothing exists at path`(recursive: Boolean) =
        runTest {
            shouldNotThrowAny {
                repository.deleteAssetsByPath("root.users.123", recursive)
            }
        }

    @Test
    fun `entryId is always the next highest value`() =
        runTest {
            val dto1 = createAssetDto("root.users.123")
            val dto2 = createAssetDto("root.users.123")
            val assetAndVariants1 = repository.store(dto1)
            val assetAndVariants2 = repository.store(dto2)
            assetAndVariants1.asset.entryId shouldBe 0
            assetAndVariants2.asset.entryId shouldBe 1
            repository.deleteAssetByPath("root.users.123")

            val dto3 = createAssetDto("root.users.123")
            val assetAndVariants3 = repository.store(dto3)
            assetAndVariants3.asset.entryId shouldBe 1

            repository.deleteAssetByPath("root.users.123", entryId = 0)
            val dto4 = createAssetDto("root.users.123")
            val assetAndVariants4 = repository.store(dto4)
            assetAndVariants4.asset.entryId shouldBe 2
        }

    @Test
    fun `can store and fetch a variant`() =
        runTest {
            val dto = createAssetDto("root.users.123")
            val persistedAssetAndVariants = repository.store(dto)

            val persistResult =
                PersistResult(
                    bucket = "bucket",
                    key = "key",
                )
            val imageAttributes =
                ImageAttributes(
                    width = 10,
                    height = 10,
                    mimeType = "image/png",
                )

            val newVariant =
                repository.storeVariant(
                    StoreAssetVariantDto(
                        treePath = persistedAssetAndVariants.asset.path,
                        entryId = persistedAssetAndVariants.asset.entryId,
                        persistResult = persistResult,
                        imageAttributes = imageAttributes,
                        lqips = LQIPs.NONE,
                    ),
                )
            newVariant.asset shouldBe persistedAssetAndVariants.asset
            newVariant.variants shouldHaveSize 1
            newVariant.variants.first().apply {
                attributes.height shouldBe imageAttributes.height
                attributes.width shouldBe imageAttributes.width
                attributes.mimeType shouldBe imageAttributes.mimeType
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
            val imageAttributes =
                ImageAttributes(
                    width = 100,
                    height = 100,
                    mimeType = "image/png",
                )

            shouldThrow<IllegalArgumentException> {
                repository.storeVariant(
                    StoreAssetVariantDto(
                        treePath = "path.does.not.exist",
                        entryId = 1,
                        persistResult = persistResult,
                        imageAttributes = imageAttributes,
                        lqips = LQIPs.NONE,
                    ),
                )
            }
        }

    @Test
    fun `cannot store a duplicate variant`() =
        runTest {
            val dto = createAssetDto("root.users.123")
            val persistedAssetAndVariants = repository.store(dto)

            val persistResult =
                PersistResult(
                    bucket = "bucket",
                    key = "key",
                )
            val imageAttributes =
                ImageAttributes(
                    width = 50,
                    height = 100,
                    mimeType = "image/png",
                )

            val variantDto =
                StoreAssetVariantDto(
                    treePath = persistedAssetAndVariants.asset.path,
                    entryId = persistedAssetAndVariants.asset.entryId,
                    persistResult = persistResult,
                    imageAttributes = imageAttributes,
                    lqips = LQIPs.NONE,
                )
            val newVariant = repository.storeVariant(variantDto)

            newVariant.asset shouldBe persistedAssetAndVariants.asset
            newVariant.variants shouldHaveSize 1
            newVariant.variants.first { !it.isOriginalVariant }.apply {
                attributes.height shouldBe imageAttributes.height
                attributes.width shouldBe imageAttributes.width
                attributes.mimeType shouldBe imageAttributes.mimeType
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
            treePath = treePath,
            request =
                StoreAssetRequest(
                    type = "image/png",
                    alt = "an image",
                ),
            imageAttributes =
                ImageAttributes(
                    mimeType = "image/png",
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
