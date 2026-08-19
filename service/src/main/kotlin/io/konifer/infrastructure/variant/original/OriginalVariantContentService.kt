package io.konifer.infrastructure.variant.original

import app.photofox.vipsffm.Vips
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.RuleDefinitionRepository
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.rules.RuleDecision
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import io.konifer.domain.rules.UploadRuleDecision
import io.konifer.domain.rules.toDecision
import io.konifer.domain.rules.upload.DefaultRuleAction
import io.konifer.domain.rules.upload.UploadRule
import io.konifer.domain.rules.upload.UploadRuleset
import io.konifer.infrastructure.rules.RuleDecisionEngine
import io.konifer.infrastructure.rules.RuleEvaluator
import io.konifer.infrastructure.variant.Siglip2TensorTransformation
import io.konifer.infrastructure.vips.decode.VipsThumbnailDecoder
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
        sourceFile: Path,
    ): UploadRuleDecision =
        withContext(Dispatchers.IO) {
            var decision = uploadRuleset.default.toDecision()
            var evaluationResult = RuleDefinitionsEvaluationResult.none
            var preprocessOutput: PreprocessOutput? = null
            Vips.run { arena ->
                val source =
                    VipsThumbnailDecoder.decode(
                        arena = arena,
                        transformation = transformationDataContainer.transformation,
                        sourceFormat = sourceFormat,
                        sourceFile = sourceFile,
                    )

                canProcess(
                    arena = arena,
                    sourceFile = sourceFile,
                    uploadRuleset = uploadRuleset,
                    currentDecision = decision,
                    sourceFormat = sourceFormat,
                ).also {
                    evaluationResult = it.first
                    decision = it.second
                }

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
                return@withContext UploadRuleDecision.Rejected(
                    ruleDefinitionsEvaluationResult = RuleDefinitionsEvaluationResult.none,
                    violationResponses = decision.violationResponses,
                )
            }
            when (checkNotNull(preprocessOutput)) {
                PreprocessOutput.SourceTransformed -> Unit
                PreprocessOutput.SourceNotTransformed ->
                    sourceFile
                        .toFile()
                        .readChannel()
                        .copyAndClose(transformationDataContainer.output)
            }
            UploadRuleDecision.Success(
                labels = decision.labels,
                ruleDefinitionsEvaluationResult = evaluationResult,
            )
        }

    private fun canProcess(
        arena: Arena,
        sourceFile: Path,
        uploadRuleset: UploadRuleset,
        currentDecision: RuleDecision,
        sourceFormat: ImageFormat,
    ): Pair<RuleDefinitionsEvaluationResult, RuleDecision> {
        val rulesToEvaluate =
            determineRulesToEvaluate(uploadRuleset)
                .takeIf { it.isNotEmpty() }
                ?: return Pair(RuleDefinitionsEvaluationResult.none, currentDecision)

        val imageTensor =
            vipsTensorProcessor.process(
                sourceFile = sourceFile,
                sourceFormat = sourceFormat,
                arena = arena,
                tensorTransformation = Siglip2TensorTransformation,
            )

        val ruleDefinitions =
            rulesToEvaluate
                .map { ruleDefinitionRepository.value.fetch(it.rule) }
                .distinctBy { it.name }
        val result =
            ruleEvaluator.value.evaluate(
                ruleDefinitions = ruleDefinitions,
                tensor = imageTensor,
            )

        // Iterator through rules and determine match
        return Pair(
            first = result,
            second =
                RuleDecisionEngine.makeDecision(
                    uploadRuleset = uploadRuleset,
                    evaluationResult = result,
                ),
        )
    }

    private fun determineRulesToEvaluate(uploadRuleset: UploadRuleset): List<UploadRule> =
        when (uploadRuleset.default) {
            DefaultRuleAction.ACCEPT -> uploadRuleset.rejectRules
            DefaultRuleAction.REJECT -> uploadRuleset.acceptRules
        } + uploadRuleset.editRules
}
