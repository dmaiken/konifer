package io.konifer.infrastructure.vips.pipeline

import app.photofox.vipsffm.VImage
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.decode.DecodedVipsImage
import io.konifer.infrastructure.vips.premultiplyIfNecessary
import io.konifer.infrastructure.vips.transformer.AlphaState
import io.konifer.infrastructure.vips.transformer.VipsTransformer
import io.konifer.infrastructure.vips.unPremultiplyIfNecessary
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import java.lang.foreign.Arena

class VipsPipelineBuilder {
    private val transformers = mutableListOf<VipsTransformer>()

    fun add(transformer: VipsTransformer) {
        transformers.add(transformer)
    }

    fun build(): VipsPipeline = VipsPipeline(transformers)
}

class VipsPipeline(
    private val transformers: List<VipsTransformer>,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    fun run(
        arena: Arena,
        source: DecodedVipsImage,
        transformation: Transformation,
    ): VipsPipelineResult {
        val appliedTransformations = source.appliedTransformations.toMutableList()
        val alreadyAppliedTransformations =
            appliedTransformations
                .filter { it.exceptionMessage == null }
                .map { it.name }
                .toSet()
        var isAlphaPremultiplied = false
        var requiresLqipRegeneration = source.requiresLqipRegeneration
        var processed = VipsTransformationResult.new(source.image)
        var failed = false

        // Filter out transformations already applied
        val transformersToExecute = transformers.filterNot { it.name in alreadyAppliedTransformations }
        for (transformer in transformersToExecute) {
            if (failed) {
                break
            }
            if (transformer.requiresTransformation(
                    arena = arena,
                    source = processed.processed,
                    transformation = transformation,
                    appliedTransformations = appliedTransformations,
                )
            ) {
                val source =
                    prepareForNextTransformation(
                        transformer = transformer,
                        processed = processed.processed,
                        isAlphaPremultiplied = isAlphaPremultiplied,
                    ).also { isAlphaPremultiplied = it.second }.first

                try {
                    processed =
                        transformer.transform(
                            arena = arena,
                            source = source,
                            transformation = transformation,
                        )
                    appliedTransformations.add(
                        AppliedTransformation(
                            name = transformer.name,
                            exceptionMessage = null,
                        ),
                    )
                    requiresLqipRegeneration = requiresLqipRegeneration || processed.requiresLqipRegeneration
                } catch (e: Exception) {
                    logger.error("Vips pipeline failed! Pipeline results: $appliedTransformations", e)
                    failed = true
                    appliedTransformations.add(
                        AppliedTransformation(
                            name = transformer.name,
                            exceptionMessage = e.message,
                        ),
                    )
                }
            }
        }

        if (!failed) {
            logger.debug { "Successfully processed image with transformation: $transformation with results: $appliedTransformations" }
        }

        return VipsPipelineResult(
            successful = !failed,
            processed = processed.processed.unPremultiplyIfNecessary(isAlphaPremultiplied),
            requiresLqipRegeneration = requiresLqipRegeneration,
            appliedTransformations = appliedTransformations,
        )
    }

    private fun prepareForNextTransformation(
        transformer: VipsTransformer,
        processed: VImage,
        isAlphaPremultiplied: Boolean,
    ): Pair<VImage, Boolean> {
        var newAlphaState = isAlphaPremultiplied
        return when (transformer.requiresAlphaState) {
            AlphaState.PREMULTIPLIED -> {
                processed.premultiplyIfNecessary(isAlphaPremultiplied).let {
                    newAlphaState = it.second
                    it.first
                }
            }
            AlphaState.UN_PREMULTIPLIED -> {
                processed.unPremultiplyIfNecessary(isAlphaPremultiplied).also {
                    newAlphaState = false
                }
            }
            AlphaState.EITHER -> processed
        }.let {
            Pair(it, newAlphaState)
        }
    }
}

fun vipsPipeline(initializer: VipsPipelineBuilder.() -> Unit): VipsPipelineBuilder = VipsPipelineBuilder().apply(initializer)

data class VipsTransformationResult(
    val processed: VImage,
    /**
     * If true, a new LQIP(s) will need to be generated for the [processed] image.
     */
    val requiresLqipRegeneration: Boolean,
) {
    companion object Factory {
        fun new(source: VImage): VipsTransformationResult =
            VipsTransformationResult(
                processed = source,
                requiresLqipRegeneration = false,
            )
    }
}

data class VipsPipelineResult(
    val successful: Boolean,
    val processed: VImage,
    val requiresLqipRegeneration: Boolean,
    val appliedTransformations: List<AppliedTransformation>,
)

data class AppliedTransformation(
    val name: String,
    val exceptionMessage: String?,
) {
    override fun toString(): String =
        if (exceptionMessage == null) {
            "Successfully applied transformation $name"
        } else {
            "Failed transformation $name: $exceptionMessage"
        }
}
