package io.konifer.client.rules

import io.konifer.client.KoniferResult
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.common.image.ImageFormat
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.util.function.Supplier

/** Blocking source-selection stage for a rule evaluation. */
class BlockingBlankRuleEvaluation internal constructor(
    private val selection: BlankRuleEvaluation,
) {
    /** Uses the image at [url] as the evaluation source. */
    fun fromUrl(url: String): BlockingNewRuleEvaluation = BlockingNewRuleEvaluation(selection.fromUrl(url))

    /** Uses the S3 object identified by [s3Arn] as the evaluation source. */
    fun fromS3Arn(s3Arn: String): BlockingNewRuleEvaluation = BlockingNewRuleEvaluation(selection.fromS3Arn(s3Arn))

    /** Uses a snapshot of [bytes] as the evaluation source. */
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

/** Blocking immutable rule evaluation with a selected image source. */
class BlockingNewRuleEvaluation internal constructor(
    private val selection: NewRuleEvaluation,
) {
    /** Appends a snapshot of [definition] to this evaluation. */
    fun withDefinition(definition: RuleDefinitionRequest): BlockingNewRuleEvaluation =
        BlockingNewRuleEvaluation(selection.withDefinition(definition))

    /** Appends snapshots of [definitions] to this evaluation. */
    fun withDefinitions(definitions: List<RuleDefinitionRequest>): BlockingNewRuleEvaluation =
        BlockingNewRuleEvaluation(selection.withDefinitions(definitions))

    /** Evaluates the accumulated definitions against the selected image. */
    fun evaluate(): KoniferResult<EvaluateRuleDefinitionsResponse> = runBlocking { selection.evaluate() }
}
