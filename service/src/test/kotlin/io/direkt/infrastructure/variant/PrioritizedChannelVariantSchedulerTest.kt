package io.direkt.infrastructure.variant

import io.direkt.domain.ports.VariantGenerator
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.RequestedTransformation
import io.direkt.domain.image.Transformation
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
            val deferred =
                scheduler.preProcessOriginalVariant(
                    sourceFormat = sourceFormat,
                    lqipImplementations = lqipImplementations,
                    transformation = transformation,
                    source = source,
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
            val path = "path"
            val entryId = 3L
            val lqipImplementations = setOf(LQIPImplementation.THUMBHASH)
            val bucket = "bucket"
            val requestedTransformations = listOf(RequestedTransformation.ORIGINAL_VARIANT)

            scheduler.initiateEagerVariants(
                path = path,
                entryId = entryId,
                requestedTransformations = requestedTransformations,
                lqipImplementations = lqipImplementations,
                bucket = bucket,
            )

            val sent = backgroundChannel.receiveCatching().getOrNull()
            sent shouldNotBe null
            with(sent!! as EagerVariantGenerationJob) {
                this.path shouldBe path
                this.entryId shouldBe entryId
                this.lqipImplementations shouldBe lqipImplementations
                this.requestedTransformations shouldBe requestedTransformations
                this.bucket shouldBe bucket
                this.deferredResult shouldBe null
            }
        }

    @Test
    fun `on-demand variants are scheduled on high-priority channel`() =
        runTest {
            val path = "path"
            val entryId = 3L
            val lqipImplementations = setOf(LQIPImplementation.THUMBHASH)
            val bucket = "bucket"
            val transformation = Transformation.ORIGINAL_VARIANT

            val deferred =
                scheduler.generateOnDemandVariant(
                    path = path,
                    entryId = entryId,
                    transformation = transformation,
                    lqipImplementations = lqipImplementations,
                    bucket = bucket,
                )

            val sent = highPriorityChannel.receiveCatching().getOrNull()
            sent shouldNotBe null
            with(sent!! as OnDemandVariantGenerationJob) {
                this.path shouldBe path
                this.entryId shouldBe entryId
                this.lqipImplementations shouldBe lqipImplementations
                this.transformation shouldBe transformation
                this.bucket shouldBe bucket
                this.deferredResult shouldBe deferred
            }
            deferred.cancel()
        }
}
