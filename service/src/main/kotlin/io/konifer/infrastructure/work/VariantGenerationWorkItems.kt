package io.konifer.infrastructure.work

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.rules.UploadRuleDecision
import io.konifer.domain.rules.upload.UploadRuleset
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Path

data class GenerateVariantsWorkItem(
    val source: Path,
    val transformationDataContainers: List<TransformationDataContainer>,
    val lqipImplementations: Set<LQIPImplementation>,
    override val deferredResult: CompletableDeferred<Unit>,
) : WorkItem<Unit>

data class ProcessOriginalVariantContentWorkItem(
    val source: Path,
    val sourceFormat: ImageFormat,
    val lqipImplementations: Set<LQIPImplementation>,
    val transformationDataContainer: TransformationDataContainer,
    val uploadRuleset: UploadRuleset,
    override val deferredResult: CompletableDeferred<UploadRuleDecision>,
) : WorkItem<UploadRuleDecision>
