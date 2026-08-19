package io.konifer.infrastructure.rules.evaluate

import app.photofox.vipsffm.Vips
import io.konifer.common.image.ImageFormat
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import io.konifer.infrastructure.rules.RuleEvaluator
import io.konifer.infrastructure.variant.Siglip2TensorTransformation
import io.konifer.infrastructure.vips.processor.VipsTensorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

class RuleDefinitionEvaluationService(
    private val ruleEvaluator: Lazy<RuleEvaluator>,
    private val vipsTensorProcessor: VipsTensorProcessor,
) {
    suspend fun evaluate(
        sourceFile: Path,
        sourceFormat: ImageFormat,
        ruleDefinitions: List<RuleDefinition>,
    ): RuleDefinitionsEvaluationResult =
        withContext(Dispatchers.IO) {
            var evaluationResult: RuleDefinitionsEvaluationResult? = null
            Vips.run { arena ->

                val imageTensor =
                    vipsTensorProcessor.process(
                        sourceFile = sourceFile,
                        arena = arena,
                        sourceFormat = sourceFormat,
                        tensorTransformation = Siglip2TensorTransformation,
                    )

                evaluationResult =
                    ruleEvaluator.value.evaluate(
                        ruleDefinitions = ruleDefinitions,
                        tensor = imageTensor,
                    )
            }
            checkNotNull(evaluationResult)
        }
}
