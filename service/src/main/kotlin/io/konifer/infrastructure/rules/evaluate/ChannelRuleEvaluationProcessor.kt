package io.konifer.infrastructure.rules.evaluate

import io.konifer.common.image.ImageFormat
import io.konifer.domain.ports.RuleEvaluationProcessor
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import io.konifer.infrastructure.work.EvaluateRuleDefinitionsWorkItem
import io.konifer.infrastructure.work.WorkItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.nio.file.Path

class ChannelRuleEvaluationProcessor(
    private val highPriorityChannel: Channel<WorkItem<*>>,
) : RuleEvaluationProcessor {
    override suspend fun evaluate(
        sourceFormat: ImageFormat,
        source: Path,
        ruleDefinitions: List<RuleDefinition>,
    ): CompletableDeferred<RuleDefinitionsEvaluationResult> {
        val deferred = CompletableDeferred<RuleDefinitionsEvaluationResult>()

        highPriorityChannel.send(
            EvaluateRuleDefinitionsWorkItem(
                sourceFormat = sourceFormat,
                source = source,
                ruleDefinitions = ruleDefinitions,
                deferredResult = deferred,
            ),
        )

        return deferred
    }
}
