package io.konifer.infrastructure.variant.original

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.ContentProcessorResult
import io.konifer.domain.ports.RuleDefinitionRepository
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.rules.RuleDecision
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.toDecision
import io.konifer.domain.rules.upload.DefaultRuleAction
import io.konifer.domain.rules.upload.UploadRule
import io.konifer.domain.rules.upload.UploadRuleset
import io.konifer.infrastructure.rules.RuleDecisionEngine
import io.konifer.infrastructure.rules.RuleEvaluator
import io.konifer.infrastructure.variant.Siglip2TensorTransformation
import io.konifer.infrastructure.vips.createDecoderOptions
import io.konifer.infrastructure.vips.processor.PreprocessOutput
import io.konifer.infrastructure.vips.processor.VipsImageProcessor
import io.konifer.infrastructure.vips.processor.VipsTensorProcessor
import io.ktor.util.cio.readChannel
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.foreign.Arena
import java.nio.file.Path

class OriginalVariantContentService(
    private val ruleEvaluator: Lazy<RuleEvaluator>,
    private val vipsTensorProcessor: VipsTensorProcessor,
    private val ruleDefinitionRepository: Lazy<RuleDefinitionRepository>,
    private val vipsImageProcessor: VipsImageProcessor,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun process(
        uploadRuleset: UploadRuleset,
        transformationDataContainer: TransformationDataContainer,
        lqipImplementations: Set<LQIPImplementation>,
        sourceFormat: ImageFormat,
        source: Path,
    ): ContentProcessorResult =
        withContext(Dispatchers.IO) {
            var decision = uploadRuleset.default.toDecision(uploadRuleset.labelRules)
            var preprocessOutput: PreprocessOutput? = null
            Vips.run { arena ->
                val source =
                    decodeSource(
                        arena = arena,
                        transformationDataContainer = transformationDataContainer,
                        sourceFormat = sourceFormat,
                        source = source,
                    )

                decision =
                    canProcess(
                        arena = arena,
                        source = source.copy(),
                        uploadRuleset = uploadRuleset,
                    )

                // If rules allow, preprocess variant
                if (decision.accept) {
                    logger.info("Asset is accepted by rules, continuing with preprocessing")
                    preprocessOutput =
                        vipsImageProcessor.preprocess(
                            arena = arena,
                            source = source,
                            transformationDataContainer = transformationDataContainer,
                            lqipImplementations = lqipImplementations,
                            sourceFormat = sourceFormat,
                        )
                }
            }

            if (!decision.accept) {
                logger.info("Asset is rejected due to rule violation")
                transformationDataContainer.output.close()
                return@withContext ContentProcessorResult.Rejected(decision.violationResponses)
            }
            when (checkNotNull(preprocessOutput)) {
                PreprocessOutput.SourceTransformed -> Unit
                PreprocessOutput.SourceNotTransformed ->
                    source
                        .toFile()
                        .readChannel()
                        .copyAndClose(transformationDataContainer.output)
            }
            ContentProcessorResult.Success
        }

    private fun canProcess(
        arena: Arena,
        source: VImage,
        uploadRuleset: UploadRuleset,
    ): RuleDecision {
        val acceptanceRulesToEvaluate =
            when (uploadRuleset.default) {
                DefaultRuleAction.ACCEPT -> uploadRuleset.rejectRules
                DefaultRuleAction.REJECT -> uploadRuleset.acceptRules
            }
        if (acceptanceRulesToEvaluate.isEmpty()) return uploadRuleset.default.toDecision(uploadRuleset.labelRules)
        val rulesToEvaluate = acceptanceRulesToEvaluate + uploadRuleset.labelRules

        val imageTensor =
            vipsTensorProcessor.process(
                source = source,
                arena = arena,
                transformation = Siglip2TensorTransformation,
            )

        val definitionsByRule =
            mutableMapOf<UploadRule, RuleDefinition>().also { map ->
                rulesToEvaluate.associateWithTo(map) { ruleDefinitionRepository.value.fetch(it.rule) }
            }
        val result =
            ruleEvaluator.value.evaluate(
                ruleDefinitions = definitionsByRule.values.toList(),
                tensor = imageTensor,
            )

        // Iterator through rules and determine match
        return RuleDecisionEngine.makeDecision(
            uploadRuleset = uploadRuleset,
            evaluationResult = result,
            definitionsByRule = definitionsByRule,
        )
    }

    private fun decodeSource(
        arena: Arena,
        transformationDataContainer: TransformationDataContainer,
        sourceFormat: ImageFormat,
        source: Path,
    ): VImage {
        val decoderOptions =
            createDecoderOptions(
                sourceFormat = sourceFormat,
                destinationFormat = transformationDataContainer.transformation.format,
            )
        return VImage.newFromFile(arena, source.toFile().absolutePath, *decoderOptions)
    }
}
