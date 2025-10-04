package io.asset.handler

import image.model.ImageFormat
import image.model.RequestedImageTransformation
import image.model.Transformation
import io.BaseUnitTest
import io.createRequestedImageTransformation
import io.image.model.Filter
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
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

class RequestedTransformationNormalizerTest : BaseUnitTest() {
    companion object {
        @JvmStatic
        fun rotateFlipSource() =
            listOf(
                // 1
                arguments(Rotate.ZERO, Flip.NONE, Rotate.ZERO, false),
                // 2
                arguments(Rotate.ONE_HUNDRED_EIGHTY, Flip.H, Rotate.ONE_HUNDRED_EIGHTY, true),
                arguments(Rotate.ZERO, Flip.V, Rotate.ONE_HUNDRED_EIGHTY, true),
                // 3
                arguments(Rotate.ONE_HUNDRED_EIGHTY, Flip.NONE, Rotate.ONE_HUNDRED_EIGHTY, false),
                // 4
                arguments(Rotate.ZERO, Flip.H, Rotate.ZERO, true),
                arguments(Rotate.ONE_HUNDRED_EIGHTY, Flip.V, Rotate.ZERO, true),
                // 5
                arguments(Rotate.TWO_HUNDRED_SEVENTY, Flip.H, Rotate.TWO_HUNDRED_SEVENTY, true),
                arguments(Rotate.NINETY, Flip.V, Rotate.TWO_HUNDRED_SEVENTY, true),
                // 6
                arguments(Rotate.TWO_HUNDRED_SEVENTY, Flip.NONE, Rotate.TWO_HUNDRED_SEVENTY, false),
                // 7
                arguments(Rotate.NINETY, Flip.H, Rotate.NINETY, true),
                arguments(Rotate.TWO_HUNDRED_SEVENTY, Flip.V, Rotate.NINETY, true),
                // 8
                arguments(Rotate.NINETY, Flip.NONE, Rotate.NINETY, false),
            )
    }

    private val requestedTransformationNormalizer =
        RequestedTransformationNormalizer(
            assetRepository = assetRepository,
        )

    @Nested
    inner class NormalizeSingleRequestedTransformationTests {
        @Test
        fun `can normalize requested transformation`() =
            runTest {
                val asset = storeAsset()
                val requested =
                    createRequestedImageTransformation(
                        width = 200,
                        height = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
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
                    createRequestedImageTransformation(
                        width = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
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
                    createRequestedImageTransformation(
                        width = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
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
                    createRequestedImageTransformation(
                        height = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
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
                    createRequestedImageTransformation(
                        height = 200,
                        fit = Fit.SCALE,
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

        @Test
        fun `can normalize filter and not fetch original variant`() =
            runTest {
                val asset = storeAsset()
                val requested =
                    createRequestedImageTransformation(
                        height = 100,
                        width = 100,
                        format = ImageFormat.PNG,
                        filter = Filter.GREYSCALE,
                    )
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        treePath = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requested = requested,
                    )

                normalized.filter shouldBe Filter.GREYSCALE

                coVerify(exactly = 0) {
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
                    createRequestedImageTransformation(
                        width = 200,
                        height = 200,
                        fit = Fit.SCALE,
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
                    createRequestedImageTransformation(
                        height = 200,
                        format = ImageFormat.PNG,
                        fit = fit,
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
                    createRequestedImageTransformation(
                        height = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
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
                    createRequestedImageTransformation(
                        width = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
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
                    createRequestedImageTransformation(
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
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

                coVerify {
                    assetRepository.fetchByPath(asset.asset.path, asset.asset.entryId, Transformation.ORIGINAL_VARIANT)
                }
            }
    }

    @Nested
    inner class NormalizeListOfRequestedTransformationTest {
        @Test
        fun `can normalize multiple requested transformations`() =
            runTest {
                val asset = storeAsset()
                val requested1 =
                    createRequestedImageTransformation(
                        width = 200,
                        height = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                    )
                val requested2 =
                    createRequestedImageTransformation(
                        width = 300,
                        height = 300,
                        format = ImageFormat.JPEG,
                        fit = Fit.SCALE,
                    )
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        originalVariantAttributes = asset.getOriginalVariant().attributes,
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
                    createRequestedImageTransformation(
                        width = 200,
                        format = ImageFormat.PNG,
                        fit = Fit.SCALE,
                    )
                val requested2 =
                    createRequestedImageTransformation(
                        height = 300,
                        format = ImageFormat.JPEG,
                        fit = Fit.SCALE,
                    )
                val normalized =
                    requestedTransformationNormalizer.normalize(
                        originalVariantAttributes = asset.getOriginalVariant().attributes,
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

    @Nested
    inner class NormalizeRotateFlipTests {
        @ParameterizedTest
        @MethodSource("io.asset.handler.RequestedTransformationNormalizerTest#rotateFlipSource")
        fun `can normalize rotation and flip transformation attributes`(
            suppliedRotate: Rotate,
            suppliedFlip: Flip,
            expectedRotate: Rotate,
            expectedHorizontalFlip: Boolean,
        ) = runTest {
            val asset = storeAsset()
            val requested =
                createRequestedImageTransformation(
                    width = 20,
                    height = 20,
                    format = ImageFormat.PNG,
                    fit = Fit.SCALE,
                    rotate = suppliedRotate,
                    flip = suppliedFlip,
                )
            val normalized =
                shouldNotThrowAny {
                    requestedTransformationNormalizer.normalize(
                        treePath = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requested = requested,
                    )
                }

            normalized.rotate shouldBe expectedRotate
            normalized.horizontalFlip shouldBe expectedHorizontalFlip

            coVerify(exactly = 0) {
                assetRepository.fetchByPath(any(), any(), any())
            }
        }
    }
}
