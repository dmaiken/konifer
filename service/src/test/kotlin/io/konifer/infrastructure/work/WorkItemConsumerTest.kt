package io.konifer.infrastructure.work

import io.konifer.BaseUnitTest
import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.rules.EvaluationScore
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import io.konifer.domain.rules.RuleEvaluationResult
import io.konifer.domain.rules.RuleName
import io.konifer.domain.rules.RulePrompt
import io.konifer.domain.rules.UploadRuleDecision
import io.konifer.domain.rules.upload.UploadRuleset
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.getResourceAsFile
import io.konifer.infrastructure.TemporaryFileFactory
import io.konifer.infrastructure.TemporaryFileFactory.createProcessedVariantTempFile
import io.konifer.infrastructure.rules.evaluate.RuleDefinitionEvaluationService
import io.konifer.infrastructure.variant.original.OriginalVariantContentService
import io.konifer.infrastructure.vips.processor.VipsImageProcessor
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.toByteArray
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.apache.commons.io.file.PathUtils.deleteOnExit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

class WorkItemConsumerTest : BaseUnitTest() {
    private val imageProcessor =
        spyk<VipsImageProcessor>(
            VipsImageProcessor(),
        )
    private val originalVariantContentService =
        mockk<OriginalVariantContentService>()
    private val ruleDefinitionEvaluationService =
        mockk<RuleDefinitionEvaluationService>()
    private val channel = Channel<WorkItem<*>>()

    // This is needed despite what Intellij thinks - it consumes from the WorkItem channel
    private val workItemConsumer =
        WorkItemConsumer(
            imageProcessor = imageProcessor,
            consumer =
                PriorityChannelConsumer(
                    highPriorityChannel = channel,
                    backgroundChannel = Channel(),
                    highPriorityWeight = 80,
                ),
            numberOfWorkers = 4,
            originalVariantContentService = originalVariantContentService,
            ruleDefinitionEvaluationService = lazy { ruleDefinitionEvaluationService },
        )

    lateinit var source: Path
    private lateinit var bufferedImage: BufferedImage

    @BeforeEach
    fun beforeEach(): Unit =
        runBlocking {
            val imageFile = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
            bufferedImage = ImageIO.read(ByteArrayInputStream(imageFile.readBytes()))
            source =
                TemporaryFileFactory.createOriginalVariantTempFile(ImageFormat.PNG.extension).apply {
                    deleteOnExit(this)
                    writeBytes(imageFile.readBytes())
                }
        }

    @Nested
    inner class VariantGenerationTests {
        @Test
        fun `can generate variant from channel`() =
            runTest {
                val output = ByteChannel()
                val result = CompletableDeferred<Unit>()
                val variantGenerationWorkItem =
                    GenerateVariantsWorkItem(
                        source = source,
                        transformationDataContainers =
                            listOf(
                                TransformationDataContainer(
                                    transformation =
                                        Transformation(
                                            height = 200.toDimension(),
                                            width = 200.toDimension(),
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
                                            colorSpace = ColorSpace.SRGB,
                                        ),
                                    output = output,
                                ),
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                    )
                channel.send(variantGenerationWorkItem)
                result.await()

                val outputImage = ImageIO.read(ByteArrayInputStream(output.toByteArray()))
                outputImage.width shouldBe 200
                outputImage.height shouldBe 200
            }

        @Test
        fun `can generate multiple variants for same image through single channel request`() =
            runTest {
                val output1 = ByteChannel()
                val output2 = ByteChannel()
                val result = CompletableDeferred<Unit>()
                val variantGenerationWorkItem =
                    GenerateVariantsWorkItem(
                        source = source,
                        transformationDataContainers =
                            listOf(
                                TransformationDataContainer(
                                    transformation =
                                        Transformation(
                                            height = 200.toDimension(),
                                            width = 200.toDimension(),
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
                                            colorSpace = ColorSpace.SRGB,
                                        ),
                                    output = output1,
                                ),
                                TransformationDataContainer(
                                    transformation =
                                        Transformation(
                                            height = 100.toDimension(),
                                            width = 100.toDimension(),
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
                                            colorSpace = ColorSpace.SRGB,
                                        ),
                                    output = output2,
                                ),
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                    )
                channel.send(variantGenerationWorkItem)
                result.await()

                val outputImage1 = ImageIO.read(ByteArrayInputStream(output1.toByteArray()))
                outputImage1.width shouldBe 200
                outputImage1.height shouldBe 200
                val outputImage2 = ImageIO.read(ByteArrayInputStream(output2.toByteArray()))
                outputImage2.width shouldBe 100
                outputImage2.height shouldBe 100
            }

        @Test
        fun `if no variants are in request then nothing is processed`() =
            runTest {
                val output =
                    createProcessedVariantTempFile(ImageFormat.PNG.extension).apply {
                        deleteOnExit(this)
                    }
                val result = CompletableDeferred<Unit>()
                val variantGenerationWorkItem =
                    GenerateVariantsWorkItem(
                        source = source,
                        transformationDataContainers = listOf(),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                    )
                channel.send(variantGenerationWorkItem)
                result.await()
                output.exists() shouldBe false
            }

        @Test
        fun `if variant fails to generate then channel is still live`() =
            runTest {
                val output = ByteChannel()
                val result = CompletableDeferred<Unit>()
                val variantGenerationWorkItem =
                    GenerateVariantsWorkItem(
                        source = source,
                        transformationDataContainers =
                            listOf(
                                TransformationDataContainer(
                                    transformation =
                                        Transformation(
                                            height = 200.toDimension(),
                                            width = 200.toDimension(),
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
                                            colorSpace = ColorSpace.SRGB,
                                        ),
                                    output = output,
                                ),
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                    )

                coEvery {
                    imageProcessor.generateVariants(
                        sourceFile = any(),
                        transformationDataContainers = any(),
                        lqipImplementations = any(),
                    )
                }.throws(RuntimeException())
                    .coAndThen { callOriginal() }

                channel.send(variantGenerationWorkItem)
                shouldThrow<RuntimeException> { result.await() }

                val newResult = CompletableDeferred<Unit>()
                channel.send(variantGenerationWorkItem.copy(deferredResult = newResult))
                shouldNotThrowAny { newResult.await() }
            }
    }

    @Nested
    inner class ProcessOriginalVariantContentWorkItemTests {
        private val transformation =
            Transformation(
                height = 200.toDimension(),
                width = 200.toDimension(),
                format = ImageFormat.PNG,
                fit = Fit.FILL,
                colorSpace = ColorSpace.SRGB,
            )

        @Test
        fun `can handle original variant processing work item`() =
            runTest {
                val output = ByteChannel()
                val result = CompletableDeferred<UploadRuleDecision>()
                val uploadRuleset = UploadRuleset()
                val originalContentWorkItem =
                    ProcessOriginalVariantContentWorkItem(
                        source = source,
                        transformationDataContainer =
                            TransformationDataContainer(
                                transformation = transformation,
                                output = output,
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        sourceFormat = ImageFormat.PNG,
                        uploadRuleset = uploadRuleset,
                    )
                val processorResult =
                    UploadRuleDecision.Success(
                        ruleDefinitionsEvaluationResult = RuleDefinitionsEvaluationResult.none,
                        labels = AssetLabels.empty,
                    )
                coEvery {
                    originalVariantContentService.process(
                        uploadRuleset = uploadRuleset,
                        transformationDataContainer = originalContentWorkItem.transformationDataContainer,
                        lqipImplementations = originalContentWorkItem.lqipImplementations,
                        sourceFormat = originalContentWorkItem.sourceFormat,
                        sourceFile = originalContentWorkItem.source,
                    )
                } returns processorResult
                channel.send(originalContentWorkItem)

                result.await() shouldBe processorResult
            }

        @Test
        fun `if exception is thrown then deferred result completes exceptionally`() =
            runTest {
                val output = ByteChannel()
                val result = CompletableDeferred<UploadRuleDecision>()
                val uploadRuleset = UploadRuleset()
                val originalContentWorkItem =
                    ProcessOriginalVariantContentWorkItem(
                        source = source,
                        transformationDataContainer =
                            TransformationDataContainer(
                                transformation = transformation,
                                output = output,
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        sourceFormat = ImageFormat.PNG,
                        uploadRuleset = uploadRuleset,
                    )
                coEvery {
                    originalVariantContentService.process(
                        uploadRuleset = uploadRuleset,
                        transformationDataContainer = originalContentWorkItem.transformationDataContainer,
                        lqipImplementations = originalContentWorkItem.lqipImplementations,
                        sourceFormat = originalContentWorkItem.sourceFormat,
                        sourceFile = originalContentWorkItem.source,
                    )
                } throws IllegalStateException()
                channel.send(originalContentWorkItem)

                shouldThrow<IllegalStateException> { result.await() }
            }

        @Test
        fun `if cancellation exception is thrown then deferred result throws`() =
            runTest {
                val output = ByteChannel()
                val result = CompletableDeferred<UploadRuleDecision>()
                val uploadRuleset = UploadRuleset()
                val originalContentWorkItem =
                    ProcessOriginalVariantContentWorkItem(
                        source = source,
                        transformationDataContainer =
                            TransformationDataContainer(
                                transformation = transformation,
                                output = output,
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        sourceFormat = ImageFormat.PNG,
                        uploadRuleset = uploadRuleset,
                    )
                coEvery {
                    originalVariantContentService.process(
                        uploadRuleset = uploadRuleset,
                        transformationDataContainer = originalContentWorkItem.transformationDataContainer,
                        lqipImplementations = originalContentWorkItem.lqipImplementations,
                        sourceFormat = originalContentWorkItem.sourceFormat,
                        sourceFile = originalContentWorkItem.source,
                    )
                } throws CancellationException()
                channel.send(originalContentWorkItem)

                shouldThrow<CancellationException> { result.await() }
            }
    }

    @Nested
    inner class EvaluateRuleDefinitionsWorkItemTests {
        @Test
        fun `can handle rule definition evaluation work item`() =
            runTest {
                val ruleDefinitions = listOf(ruleDefinition())
                val result = CompletableDeferred<RuleDefinitionsEvaluationResult>()
                val workItem =
                    EvaluateRuleDefinitionsWorkItem(
                        source = source,
                        sourceFormat = ImageFormat.PNG,
                        ruleDefinitions = ruleDefinitions,
                        deferredResult = result,
                    )
                val evaluationResult =
                    RuleDefinitionsEvaluationResult(
                        results =
                            listOf(
                                RuleEvaluationResult(
                                    ruleDefinition = ruleDefinitions.single(),
                                    evaluationScore = EvaluationScore(score = 0.9, matched = true),
                                    promptScores = mapOf(RulePrompt("contains a tree") to 0.9),
                                ),
                            ),
                    )
                coEvery {
                    ruleDefinitionEvaluationService.evaluate(
                        sourceFile = workItem.source,
                        sourceFormat = workItem.sourceFormat,
                        ruleDefinitions = workItem.ruleDefinitions,
                    )
                } returns evaluationResult

                channel.send(workItem)

                result.await() shouldBe evaluationResult
            }

        @Test
        fun `if rule definition evaluation throws exception then deferred result completes exceptionally`() =
            runTest {
                val result = CompletableDeferred<RuleDefinitionsEvaluationResult>()
                val workItem =
                    EvaluateRuleDefinitionsWorkItem(
                        source = source,
                        sourceFormat = ImageFormat.PNG,
                        ruleDefinitions = listOf(ruleDefinition()),
                        deferredResult = result,
                    )
                coEvery {
                    ruleDefinitionEvaluationService.evaluate(
                        sourceFile = workItem.source,
                        sourceFormat = workItem.sourceFormat,
                        ruleDefinitions = workItem.ruleDefinitions,
                    )
                } throws IllegalStateException()

                channel.send(workItem)

                shouldThrow<IllegalStateException> { result.await() }
            }

        @Test
        fun `if rule definition evaluation is cancelled then deferred result throws`() =
            runTest {
                val result = CompletableDeferred<RuleDefinitionsEvaluationResult>()
                val workItem =
                    EvaluateRuleDefinitionsWorkItem(
                        source = source,
                        sourceFormat = ImageFormat.PNG,
                        ruleDefinitions = listOf(ruleDefinition()),
                        deferredResult = result,
                    )
                coEvery {
                    ruleDefinitionEvaluationService.evaluate(
                        sourceFile = workItem.source,
                        sourceFormat = workItem.sourceFormat,
                        ruleDefinitions = workItem.ruleDefinitions,
                    )
                } throws CancellationException()

                channel.send(workItem)

                shouldThrow<CancellationException> { result.await() }
            }

        private fun ruleDefinition(): RuleDefinition =
            RuleDefinition(
                name = RuleName("contains-tree"),
                prompts = listOf(RulePrompt("contains a tree")),
                threshold = RuleDefinitionThreshold(0.8),
            )
    }
}
