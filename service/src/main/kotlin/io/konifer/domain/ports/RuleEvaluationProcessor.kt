package io.konifer.domain.ports

import io.konifer.common.image.ImageFormat
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Path

interface RuleEvaluationProcessor {
    suspend fun evaluate(
        sourceFormat: ImageFormat,
        source: Path,
        ruleDefinitions: List<RuleDefinition>,
    ): CompletableDeferred<RuleDefinitionsEvaluationResult>
}
