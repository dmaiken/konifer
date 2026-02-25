package io.konifer.infrastructure.variant

import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantType
import io.konifer.domain.variant.Transformation
import io.konifer.service.TemporaryFileFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.channels.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.test.runTest
import org.apache.commons.io.file.PathUtils.deleteOnExit
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@OptIn(ExperimentalCoroutinesApi::class)
class PrioritizedChannelVariantSchedulerTest {
    val highPriorityChannel = Channel<ImageProcessingJob<*>>(UNLIMITED)
    val backgroundChannel = Channel<ImageProcessingJob<*>>(UNLIMITED)

    val scheduler: VariantGenerator =
        PrioritizedChannelVariantScheduler(
            highPriorityChannel = highPriorityChannel,
            backgroundChannel = backgroundChannel,
        )

    @Test
    fun `preprocessing original variants schedules job on high-priority channel`() =
        runTest {
            val sourceFormat = ImageFormat.JPEG
            val lqipImplementations = setOf(LQIPImplementation.THUMBHASH)
            val transformation =
                Transformation(
                    height = 100,
                    width = 100,
                    format = ImageFormat.JPEG,
                )
            val source =
                TemporaryFileFactory.createOriginalVariantTempFile(ImageFormat.JPEG.extension).apply {
                    deleteOnExit(this)
                }
            val output =
                TemporaryFileFactory.createOriginalVariantTempFile(ImageFormat.JPEG.extension).apply {
                    deleteOnExit(this)
                }
            val deferred =
                scheduler.preProcessOriginalVariant(
                    sourceFormat = sourceFormat,
                    lqipImplementations = lqipImplementations,
                    transformation = transformation,
                    source = source,
                    output = output,
                )

            val sent = highPriorityChannel.receiveCatching().getOrNull()
            sent shouldNotBe null
            with(sent!! as PreProcessJob) {
                this.sourceFormat shouldBe sourceFormat
                this.lqipImplementations shouldBe lqipImplementations
                this.transformation shouldBe transformation
                this.source shouldBe source
                this.deferredResult shouldBe deferred
            }
            deferred.cancel()
        }

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
            with(sent!! as GenerateVariantsJob) {
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
            with(sent!! as GenerateVariantsJob) {
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
            deferred.await() shouldBe true
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
