package io.direkt.infrastructure.variant

import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.ports.TransformationDataContainer
import io.direkt.domain.ports.VariantGenerator
import io.direkt.domain.ports.VariantType
import io.direkt.domain.variant.Transformation
import io.direkt.infrastructure.TemporaryFileFactory
import io.direkt.service.context.RequestedTransformation
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files

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
            val transformation = Transformation.ORIGINAL_VARIANT
            val source =
                Files.createTempFile("", ".tmp").toFile().apply {
                    deleteOnExit()
                }
            val output = Files.createTempFile("", ".tmp").toFile().apply {
                deleteOnExit()
            }
            val deferred =
                scheduler.preProcessOriginalVariant(
                    sourceFormat = sourceFormat,
                    lqipImplementations = lqipImplementations,
                    transformation = transformation,
                    source = source.toPath(),
                    output = output.toPath()
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
            val transformationDataContainer = TransformationDataContainer(
                transformation = Transformation.ORIGINAL_VARIANT,
                output = TemporaryFileFactory.createPreProcessedTempFile(ImageFormat.JPEG.extension)
            )
            val source = TemporaryFileFactory.createOriginalVariantTempFile(ImageFormat.JPEG.extension)

            scheduler.generateVariantsFromSource(
                source = source,
                transformationDataContainers = listOf(transformationDataContainer),
                lqipImplementations = lqipImplementations,
                variantType = VariantType.EAGER
            )

            val sent = backgroundChannel.receiveCatching().getOrNull()
            sent shouldNotBe null
            with(sent!! as GenerateVariantsJob) {
                this.source shouldBe source
                this.transformationDataContainers shouldBe listOf(transformationDataContainer)
                this.lqipImplementations shouldBe lqipImplementations
                this.deferredResult shouldBe null
            }
        }

    @Test
    fun `on-demand variants are scheduled on high-priority channel`() =
        runTest {
            val lqipImplementations = setOf(LQIPImplementation.THUMBHASH)
            val transformationDataContainer = TransformationDataContainer(
                transformation = Transformation.ORIGINAL_VARIANT,
                output = TemporaryFileFactory.createPreProcessedTempFile(ImageFormat.JPEG.extension)
            )
            val source = TemporaryFileFactory.createOriginalVariantTempFile(ImageFormat.JPEG.extension)

            scheduler.generateVariantsFromSource(
                source = source,
                transformationDataContainers = listOf(transformationDataContainer),
                lqipImplementations = lqipImplementations,
                variantType = VariantType.ON_DEMAND
            )

            val sent = highPriorityChannel.receiveCatching().getOrNull()
            sent shouldNotBe null
            with(sent!! as GenerateVariantsJob) {
                this.source shouldBe source
                this.transformationDataContainers shouldBe listOf(transformationDataContainer)
                this.lqipImplementations shouldBe lqipImplementations
                this.deferredResult shouldBe null
            }
        }
}
