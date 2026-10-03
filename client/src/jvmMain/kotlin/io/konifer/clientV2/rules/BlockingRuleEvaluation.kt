package io.konifer.clientV2.rules

import io.konifer.clientV2.KoniferV2Result
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.common.image.ImageFormat
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.util.function.Supplier

class BlockingBlankRuleEvaluation internal constructor(
    private val selection: BlankRuleEvaluation,
) {
    fun fromUrl(url: String): BlockingNewRuleEvaluation = BlockingNewRuleEvaluation(selection.fromUrl(url))

    fun fromS3Arn(s3Arn: String): BlockingNewRuleEvaluation = BlockingNewRuleEvaluation(selection.fromS3Arn(s3Arn))

    fun fromBytes(
        bytes: ByteArray,
        format: ImageFormat,
    ): BlockingNewRuleEvaluation = BlockingNewRuleEvaluation(selection.fromBytes(bytes, format))

    /** [open] must return a fresh stream for each request; the stream is closed after reading. */
    fun fromInputStream(
        open: Supplier<InputStream>,
        format: ImageFormat,
    ): BlockingNewRuleEvaluation = BlockingNewRuleEvaluation(selection.fromInputStream({ open.get() }, format))
}

class BlockingNewRuleEvaluation internal constructor(
    private val selection: NewRuleEvaluation,
) {
    fun withDefinition(definition: RuleDefinitionRequest): BlockingNewRuleEvaluation =
        BlockingNewRuleEvaluation(selection.withDefinition(definition))

    fun withDefinitions(definitions: List<RuleDefinitionRequest>): BlockingNewRuleEvaluation =
        BlockingNewRuleEvaluation(selection.withDefinitions(definitions))

    fun evaluate(): KoniferV2Result<EvaluateRuleDefinitionsResponse> = runBlocking { selection.evaluate() }
}
