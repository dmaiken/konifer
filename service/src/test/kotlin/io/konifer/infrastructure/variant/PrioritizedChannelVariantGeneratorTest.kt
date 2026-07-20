package io.konifer.infrastructure.variant

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantType
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.TemporaryFileFactory
import io.konifer.infrastructure.work.GenerateVariantsWorkItem
import io.konifer.infrastructure.work.WorkItem
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.channels.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@OptIn(ExperimentalCoroutinesApi::class)
class PrioritizedChannelVariantGeneratorTest {
    val highPriorityChannel = Channel<WorkItem<*>>(UNLIMITED)
    val backgroundChannel = Channel<WorkItem<*>>(UNLIMITED)

    val scheduler: VariantGenerator =
        PrioritizedChannelVariantGenerator(
            highPriorityChannel = highPriorityChannel,
            backgroundChannel = backgroundChannel,
        )

    @Test
    fun `eager variants are scheduled on background channel`() =
        runTest {
            val lqipImplementations = setOf(LQIPImplementation.THUMBHASH)
            val transformationDataContainer =
                TransformationDataContainer(
                    transformation =
                        Transformation(
                            height = 100,
                            width = 100,
                            format = ImageFormat.JPEG,
                            colorSpace = ColorSpace.SRGB,
                        ),
                    output = ByteChannel(),
                )
            val source = TemporaryFileFactory.createOriginalVariantTempFile(ImageFormat.JPEG.extension)

            scheduler.generateVariantsFromSource(
                source = source,
                transformationDataContainers = listOf(transformationDataContainer),
                lqipImplementations = lqipImplementations,
                variantType = VariantType.EAGER,
            )

            val sent = backgroundChannel.receiveCatching().getOrNull()
            sent shouldNotBe null
            with(sent!! as GenerateVariantsWorkItem) {
                this.source shouldBe source
                this.transformationDataContainers shouldBe listOf(transformationDataContainer)
                this.lqipImplementations shouldBe lqipImplementations
            }
        }

    @Test
    fun `on-demand variants are scheduled on high-priority channel`() =
        runTest {
            val lqipImplementations = setOf(LQIPImplementation.THUMBHASH)
            val transformationDataContainer =
                TransformationDataContainer(
                    transformation =
                        Transformation(
                            height = 100,
                            width = 100,
                            format = ImageFormat.JPEG,
                            colorSpace = ColorSpace.SRGB,
                        ),
                    output = ByteChannel(),
                )
            val source = TemporaryFileFactory.createOriginalVariantTempFile(ImageFormat.JPEG.extension)

            scheduler.generateVariantsFromSource(
                source = source,
                transformationDataContainers = listOf(transformationDataContainer),
                lqipImplementations = lqipImplementations,
                variantType = VariantType.ON_DEMAND,
            )

            val sent = highPriorityChannel.receiveCatching().getOrNull()
            sent shouldNotBe null
            with(sent!! as GenerateVariantsWorkItem) {
                this.source shouldBe source
                this.transformationDataContainers shouldBe listOf(transformationDataContainer)
                this.lqipImplementations shouldBe lqipImplementations
            }
        }

    @ParameterizedTest
    @EnumSource(VariantType::class)
    fun `returns and does nothing if no variants are defined in request`(variantType: VariantType) =
        runTest {
            val lqipImplementations = setOf(LQIPImplementation.THUMBHASH)
            val source = TemporaryFileFactory.createOriginalVariantTempFile(ImageFormat.JPEG.extension)

            val deferred =
                scheduler.generateVariantsFromSource(
                    source = source,
                    transformationDataContainers = listOf(),
                    lqipImplementations = lqipImplementations,
                    variantType = variantType,
                )

            highPriorityChannel.shouldBeEmpty()
            backgroundChannel.shouldBeEmpty()
            shouldNotThrowAny { deferred.await() }
        }

    @ParameterizedTest
    @EnumSource(VariantType::class)
    fun `throws if no transformations are for original variants`(variantType: VariantType) =
        runTest {
            val lqipImplementations = setOf(LQIPImplementation.THUMBHASH)
            val transformationDataContainer =
                TransformationDataContainer(
                    transformation = Transformation.ORIGINAL_VARIANT,
                    output = ByteChannel(),
                )
            val source = TemporaryFileFactory.createOriginalVariantTempFile(ImageFormat.JPEG.extension)

            shouldThrow<IllegalArgumentException> {
                scheduler.generateVariantsFromSource(
                    source = source,
                    transformationDataContainers = listOf(transformationDataContainer),
                    lqipImplementations = lqipImplementations,
                    variantType = variantType,
                )
            }
            highPriorityChannel.shouldBeEmpty()
            backgroundChannel.shouldBeEmpty()
        }
}
