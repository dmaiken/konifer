package io.asset.handler

import image.model.ImageFormat
import image.model.RequestedImageTransformation
import image.model.Transformation
import io.BaseUnitTest
import io.image.model.Fit
import io.image.model.Flip
import io.image.model.Rotate
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class RequestedTransformationNormalizerTest : BaseUnitTest() {
    private val requestedTransformationNormalizer =
        RequestedTransformationNormalizer(
            assetRepository = assetRepository,
        )

    @Nested
    inner class NormalizeSingleRequestedTransformationTest {
        @Test
        fun `can normalize requested transformation`() =
            runTest {
                val asset = storeAsset()
                val requested =
                    RequestedImageTransformation(
                        width = 200,
                        height = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        treePath = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requested = requested,
                    )

                normalized.fit shouldBe requested.fit
                normalized.width shouldBe requested.width
                normalized.height shouldBe requested.height
                normalized.format shouldBe requested.format

                coVerify(exactly = 0) {
                    assetRepository.fetchByPath(any(), any(), any())
                }
            }

        @Test
        fun `if requested transformation is original variant then original variant transformation is returned`() =
            runTest {
                val asset = storeAsset()
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        treePath = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requested = RequestedImageTransformation.ORIGINAL_VARIANT,
                    )

                normalized shouldBe Transformation.ORIGINAL_VARIANT

                coVerify(exactly = 0) {
                    assetRepository.fetchByPath(any(), any(), any())
                }
            }

        @Test
        fun `if original is needed and cannot be found then exception is thrown`() =
            runTest {
                val requested =
                    RequestedImageTransformation(
                        width = 200,
                        height = null,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                shouldThrow<IllegalArgumentException> {
                    requestedTransformationNormalizer.normalize(
                        treePath = "/bad/path",
                        entryId = null,
                        requested = requested,
                    )
                }

                coVerify(exactly = 1) {
                    assetRepository.fetchByPath("/bad/path", null, Transformation.ORIGINAL_VARIANT)
                }
            }

        @Test
        fun `fetches original variant if needed to normalize height`() =
            runTest {
                val asset = storeAsset()
                val requested =
                    RequestedImageTransformation(
                        width = 200,
                        height = null,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        treePath = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requested = requested,
                    )

                normalized.fit shouldBe requested.fit
                normalized.width shouldBe requested.width
                normalized.height shouldBe 200
                normalized.format shouldBe requested.format

                coVerify(exactly = 1) {
                    assetRepository.fetchByPath(asset.asset.path, asset.asset.entryId, Transformation.ORIGINAL_VARIANT)
                }
            }

        @Test
        fun `fetches original variant if needed to normalize width`() =
            runTest {
                val asset = storeAsset()
                val requested =
                    RequestedImageTransformation(
                        width = null,
                        height = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        treePath = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requested = requested,
                    )

                normalized.fit shouldBe requested.fit
                normalized.width shouldBe 200
                normalized.height shouldBe requested.height
                normalized.format shouldBe requested.format

                coVerify(exactly = 1) {
                    assetRepository.fetchByPath(asset.asset.path, asset.asset.entryId, Transformation.ORIGINAL_VARIANT)
                }
            }

        @Test
        fun `fetches original variant only once if needed to normalize multiple attributes`() =
            runTest {
                val asset = storeAsset()
                val requested =
                    RequestedImageTransformation(
                        width = null,
                        height = 200,
                        format = null,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        treePath = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requested = requested,
                    )

                normalized.fit shouldBe requested.fit
                normalized.width shouldBe 200
                normalized.height shouldBe requested.height
                normalized.format shouldBe ImageFormat.PNG

                coVerify(exactly = 1) {
                    assetRepository.fetchByPath(asset.asset.path, asset.asset.entryId, Transformation.ORIGINAL_VARIANT)
                }
            }
    }

    @Nested
    inner class NormalizeFormatAttributeTests {
        @Test
        fun `fetches original variant if needed to normalize format`() =
            runTest {
                val asset = storeAsset()
                val requested =
                    RequestedImageTransformation(
                        width = 200,
                        height = 200,
                        format = null,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        treePath = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requested = requested,
                    )
                normalized.fit shouldBe requested.fit
                normalized.width shouldBe requested.width
                normalized.height shouldBe requested.height
                normalized.format shouldBe ImageFormat.PNG
                coVerify(exactly = 1) {
                    assetRepository.fetchByPath(asset.asset.path, asset.asset.entryId, Transformation.ORIGINAL_VARIANT)
                }
            }
    }

    @Nested
    inner class NormalizeResizeAttributesTests {
        @ParameterizedTest
        @EnumSource(Fit::class, mode = EnumSource.Mode.EXCLUDE, names = ["SCALE"])
        fun `height and width are required depending on the fit`(fit: Fit) =
            runTest {
                val asset = storeAsset()
                val requested =
                    RequestedImageTransformation(
                        width = null,
                        height = 200,
                        format = ImageFormat.PNG,
                        fit = fit,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                shouldThrow<IllegalArgumentException> {
                    requestedTransformationNormalizer.normalize(
                        treePath = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requested = requested,
                    )
                }
            }

        @Test
        fun `only height is required when using scale fit`() =
            runTest {
                val asset = storeAsset()
                val requested =
                    RequestedImageTransformation(
                        width = null,
                        height = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val normalized =
                    shouldNotThrowAny {
                        requestedTransformationNormalizer.normalize(
                            treePath = asset.asset.path,
                            entryId = asset.asset.entryId,
                            requested = requested,
                        )
                    }
                normalized.fit shouldBe requested.fit
                normalized.width shouldBe 200
                normalized.height shouldBe requested.height
                normalized.format shouldBe ImageFormat.PNG
            }

        @Test
        fun `only width is required when using scale fit`() =
            runTest {
                val asset = storeAsset()
                val requested =
                    RequestedImageTransformation(
                        width = 200,
                        height = null,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val normalized =
                    shouldNotThrowAny {
                        requestedTransformationNormalizer.normalize(
                            treePath = asset.asset.path,
                            entryId = asset.asset.entryId,
                            requested = requested,
                        )
                    }
                normalized.fit shouldBe requested.fit
                normalized.width shouldBe requested.width
                normalized.height shouldBe 200
                normalized.format shouldBe ImageFormat.PNG
            }

        @Test
        fun `if height and width are null then original attributes are used for scale fit`() =
            runTest {
                val asset = storeAsset()
                val requested =
                    RequestedImageTransformation(
                        width = null,
                        height = null,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val normalized =
                    shouldNotThrowAny {
                        requestedTransformationNormalizer.normalize(
                            treePath = asset.asset.path,
                            entryId = asset.asset.entryId,
                            requested = requested,
                        )
                    }
                normalized.fit shouldBe requested.fit
                normalized.width shouldBe 100
                normalized.height shouldBe 100
                normalized.format shouldBe ImageFormat.PNG
            }
    }

    @Nested
    inner class NormalizeListOfRequestedTransformationTest {
        @Test
        fun `can normalize multiple requested transformations`() =
            runTest {
                val asset = storeAsset()
                val requested1 =
                    RequestedImageTransformation(
                        width = 200,
                        height = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val requested2 =
                    RequestedImageTransformation(
                        width = 300,
                        height = 300,
                        format = ImageFormat.JPEG,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        originalAsset = asset,
                        requested = listOf(requested1, requested2),
                    )

                normalized shouldHaveSize 2
                normalized.forAtLeastOne {
                    it.fit shouldBe requested1.fit
                    it.width shouldBe requested1.width
                    it.height shouldBe requested1.height
                    it.format shouldBe requested1.format
                }
                normalized.forAtLeastOne {
                    it.fit shouldBe requested2.fit
                    it.width shouldBe requested2.width
                    it.height shouldBe requested2.height
                    it.format shouldBe requested2.format
                }

                coVerify(exactly = 0) {
                    assetRepository.fetchByPath(any(), any(), any())
                }
            }

        @Test
        fun `can normalize multiple transformations for one asset that require the supplied original variant`() =
            runTest {
                val asset = storeAsset()
                val requested1 =
                    RequestedImageTransformation(
                        width = 200,
                        height = null,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val requested2 =
                    RequestedImageTransformation(
                        width = null,
                        height = 300,
                        format = ImageFormat.JPEG,
                        fit = Fit.SCALE,
                        rotate = Rotate.ZERO,
                        flip = Flip.NONE,
                    )
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        originalAsset = asset,
                        requested = listOf(requested1, requested2),
                    )

                normalized shouldHaveSize 2
                normalized.forAtLeastOne {
                    it.fit shouldBe requested1.fit
                    it.width shouldBe requested1.width
                    it.height shouldBe 200
                    it.format shouldBe requested1.format
                }
                normalized.forAtLeastOne {
                    it.fit shouldBe requested2.fit
                    it.width shouldBe 300
                    it.height shouldBe requested2.height
                    it.format shouldBe requested2.format
                }

                coVerify(exactly = 0) {
                    assetRepository.fetchByPath(any(), any(), any())
                }
            }
    }
}
