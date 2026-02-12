package io.konifer.infrastructure.vips.pipeline

import app.photofox.vipsffm.VImage
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.premultiplyIfNecessary
import io.konifer.infrastructure.vips.transformation.AlphaState
import io.konifer.infrastructure.vips.transformation.VipsTransformer
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
        source: VImage,
        transformation: Transformation,
    ): VipsPipelineResult {
        val appliedTransformations = mutableListOf<AppliedTransformation>()
        var isAlphaPremultiplied = false
        var requiresLqipRegeneration = false
        var processed =
            VipsTransformationResult(
                processed = source,
                requiresLqipRegeneration = false,
            )
        var failed = false

        for (transformer in transformers) {
            if (failed) {
                break
            }
            if (transformer.requiresTransformation(
                    arena = arena,
                    source = processed.processed,
                    transformation = transformation,
                )
            ) {
                val source =
                    when (transformer.requiresAlphaState) {
                        AlphaState.PREMULTIPLIED -> {
                            processed.processed.premultiplyIfNecessary(isAlphaPremultiplied).let {
                                isAlphaPremultiplied = it.second
                                it.first
                            }
                        }
                        AlphaState.UN_PREMULTIPLIED -> {
                            processed.processed.unPremultiplyIfNecessary(isAlphaPremultiplied).also {
                                isAlphaPremultiplied = false
                            }
                        }
                    }
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
}

fun vipsPipeline(initializer: VipsPipelineBuilder.() -> Unit): VipsPipelineBuilder = VipsPipelineBuilder().apply(initializer)

data class VipsTransformationResult(
    val processed: VImage,
    /**
     * If true, a new LQIP(s) will need to be generated for the [processed] image.
     */
    val requiresLqipRegeneration: Boolean,
)

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
