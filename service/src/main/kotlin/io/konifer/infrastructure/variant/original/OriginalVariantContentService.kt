package io.konifer.infrastructure.variant.original

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.domain.ports.RuleDefinitionRepository
import io.konifer.domain.rules.RuleDecisionEngine
import io.konifer.infrastructure.rules.RuleEvaluationInput
import io.konifer.infrastructure.rules.RuleEvaluator
import io.konifer.infrastructure.variant.ProcessOriginalVariantContentJob
import io.konifer.infrastructure.variant.Siglip2TensorTransformation
import io.konifer.infrastructure.vips.createDecoderOptions
import io.konifer.infrastructure.vips.processor.VipsTensorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.foreign.Arena

class OriginalVariantContentService(
    private val ruleEvaluator: RuleEvaluator,
    private val vipsTensorProcessor: VipsTensorProcessor,
    private val ruleDefinitionRepository: RuleDefinitionRepository,
    private val ruleDecisionEngine: RuleDecisionEngine,
) {

    suspend fun process(job: ProcessOriginalVariantContentJob): Unit = withContext(Dispatchers.IO) {
        val ruleDefinitions = job.uploadRuleset.allRules.map { rule ->
            ruleDefinitionRepository.fetch(rule.rule)
        }
        Vips.run { arena ->
            val source = decodeSource(arena, job)

            val imageTensor = vipsTensorProcessor.process(
                source = source.copy(),
                arena = arena,
                transformation = Siglip2TensorTransformation,
            )

            // Evaluate rules
            val result = ruleEvaluator.evaluate(
                ruleDefinitions = ruleDefinitions,
                tensor = imageTensor,
            )

            // Iterator through rules and determine match
            ruleDecisionEngine.makeDecision(
                ruleset = job.uploadRuleset,
                evaluationResult = result
            )

            // If rules allow, preprocess variant, otherwise cancel the output stream and return the failure with the message
        }
    }

    private fun decodeSource(arena: Arena, job: ProcessOriginalVariantContentJob): VImage {
        val decoderOptions =
            createDecoderOptions(
                sourceFormat = job.sourceFormat,
                destinationFormat = job.transformationDataContainer.transformation.format,
            )
        return VImage.newFromFile(arena, job.source.toFile().absolutePath, *decoderOptions)
    }
}
