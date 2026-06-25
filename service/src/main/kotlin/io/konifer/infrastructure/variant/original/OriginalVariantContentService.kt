package io.konifer.infrastructure.variant.original

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.domain.ports.RuleDefinitionRepository
import io.konifer.infrastructure.rules.RuleEvaluationInput
import io.konifer.infrastructure.rules.RuleEvaluator
import io.konifer.infrastructure.variant.ProcessOriginalVariantContentJob
import io.konifer.infrastructure.variant.Siglip2TensorTransformation
import io.konifer.infrastructure.vips.createDecoderOptions
import io.konifer.infrastructure.vips.processor.VipsTensorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OriginalVariantContentService(
    private val ruleEvaluator: RuleEvaluator,
    private val vipsTensorProcessor: VipsTensorProcessor,
    private val ruleDefinitionRepository: RuleDefinitionRepository,
) {

    suspend fun process(job: ProcessOriginalVariantContentJob): Unit = withContext(Dispatchers.IO) {
        val ruleDefinitions = job.uploadRuleset.allRules.map { rule ->
            ruleDefinitionRepository.fetch(rule.rule)
        }
        Vips.run { arena ->
            val decoderOptions =
                createDecoderOptions(
                    sourceFormat = job.sourceFormat,
                    destinationFormat = job.transformationDataContainer.transformation.format,
                )
            val source = VImage.newFromFile(arena, job.source.toFile().absolutePath, *decoderOptions)

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

            // If rules allow, preprocess variant, otherwise cancel the output stream and return the failure with the message
        }
    }
}
