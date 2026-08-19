package io.konifer.infrastructure.work

import io.konifer.infrastructure.rules.evaluate.RuleDefinitionEvaluationService
import io.konifer.infrastructure.variant.original.OriginalVariantContentService
import io.konifer.infrastructure.vips.processor.VipsImageProcessor
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WorkItemConsumer(
    private val imageProcessor: VipsImageProcessor,
    private val originalVariantContentService: OriginalVariantContentService,
    private val ruleDefinitionEvaluationService: Lazy<RuleDefinitionEvaluationService>,
    private val consumer: PriorityChannelConsumer<WorkItem<*>>,
    numberOfWorkers: Int,
) {
    /**
     * Since these jobs will interact with vips-ffm, the dispatcher must be IO
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        logger.info("Starting $numberOfWorkers variant generator workers")
        repeat(numberOfWorkers) { index ->
            start(index)
        }
    }

    fun start(index: Int) {
        scope.launch {
            while (isActive) {
                handleWorkItem(consumer.nextWorkItem())
            }
            logger.info("Shut down variant generator channel listener: $index")
        }
    }

    private suspend fun handleWorkItem(workItem: WorkItem<*>) {
        when (workItem) {
            is GenerateVariantsWorkItem -> handle(workItem)
            is ProcessOriginalVariantContentWorkItem -> handle(workItem)
            is EvaluateRuleDefinitionsWorkItem -> handle(workItem)
        }
    }

    private suspend fun handle(workItem: GenerateVariantsWorkItem) {
        logger.debug { "Handling GenerateVariantsWorkItem: $workItem" }
        try {
            imageProcessor.generateVariants(
                sourceFile = workItem.source,
                lqipImplementations = workItem.lqipImplementations,
                transformationDataContainers = workItem.transformationDataContainers,
            )
            workItem.deferredResult.complete(Unit)
        } catch (e: CancellationException) {
            workItem.deferredResult.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            logger.error("Error while generating variant with request: {}", workItem, e)
            workItem.deferredResult.completeExceptionally(e)
        }
    }

    private suspend fun handle(workItem: ProcessOriginalVariantContentWorkItem) {
        logger.debug { "Handling ProcessOriginalVariantContentWorkItem: $workItem" }
        try {
            val response =
                originalVariantContentService.process(
                    uploadRuleset = workItem.uploadRuleset,
                    transformationDataContainer = workItem.transformationDataContainer,
                    lqipImplementations = workItem.lqipImplementations,
                    sourceFormat = workItem.sourceFormat,
                    sourceFile = workItem.source,
                )
            workItem.deferredResult.complete(response)
        } catch (e: CancellationException) {
            workItem.deferredResult.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            logger.error("Error while processing original variant with request: {}", workItem, e)
            workItem.deferredResult.completeExceptionally(e)
        }
    }

    private suspend fun handle(workItem: EvaluateRuleDefinitionsWorkItem) {
        try {
            val result =
                ruleDefinitionEvaluationService.value.evaluate(
                    sourceFile = workItem.source,
                    sourceFormat = workItem.sourceFormat,
                    ruleDefinitions = workItem.ruleDefinitions,
                )
            workItem.deferredResult.complete(result)
        } catch (e: CancellationException) {
            workItem.deferredResult.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            logger.error("Error while evaluating rule definitions with request: {}", workItem, e)
            workItem.deferredResult.completeExceptionally(e)
        }
    }
}
