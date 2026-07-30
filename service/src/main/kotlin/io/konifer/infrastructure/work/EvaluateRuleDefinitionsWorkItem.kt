package io.konifer.infrastructure.work

import io.konifer.common.image.ImageFormat
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Path

data class EvaluateRuleDefinitionsWorkItem(
    val sourceFormat: ImageFormat,
    val source: Path,
    val ruleDefinitions: List<RuleDefinition>,
    override val deferredResult: CompletableDeferred<RuleDefinitionsEvaluationResult>,
) : WorkItem<RuleDefinitionsEvaluationResult>
