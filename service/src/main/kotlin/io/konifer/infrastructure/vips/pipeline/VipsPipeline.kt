package io.konifer.infrastructure.vips.pipeline

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsImageCopyMemory
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.decode.DecodedVipsImage
import io.konifer.infrastructure.vips.premultiplyIfNecessary
import io.konifer.infrastructure.vips.transformer.AlphaRequirement
import io.konifer.infrastructure.vips.transformer.PixelAccess
import io.konifer.infrastructure.vips.transformer.TransformationContext
import io.konifer.infrastructure.vips.transformer.TransformationDecision
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
        var currentPixelAccess = source.pixelAccess

        var requiresLqipRegeneration = source.requiresLqipRegeneration
        var processed = VipsTransformationResult.new(source.image)
        var failed = false

        // Filter out transformations already applied
        val transformersToExecute = transformers.filterNot { it.name in alreadyAppliedTransformations }
        for (transformer in transformersToExecute) {
            if (failed) {
                break
            }
            val decision =
                transformer.decide(
                    TransformationContext(
                        arena = arena,
                        source = processed.processed,
                        transformation = transformation,
                        appliedTransformations = appliedTransformations,
                    ),
                )
            if (decision is TransformationDecision.Apply) {
                val source =
                    prepareForNextTransformation(
                        arena = arena,
                        processed = processed.processed,
                        isAlphaPremultiplied = isAlphaPremultiplied,
                        decision = decision,
                        pixelAccess = currentPixelAccess,
                    ).also {
                        isAlphaPremultiplied = it.isAlphaPremultiplied
                        currentPixelAccess = it.currentPixelAccess
                    }.processed

                try {
                    processed =
                        transformer.transform(
                            arena = arena,
                            source = source,
                            transformation = transformation,
                        )
                    appliedTransformations.add(
                        AppliedTransformation.success(transformer.name),
                    )
                    requiresLqipRegeneration = requiresLqipRegeneration || processed.requiresLqipRegeneration
                } catch (e: Exception) {
                    failed = true
                    appliedTransformations.add(
                        AppliedTransformation(
                            name = transformer.name,
                            exceptionMessage = e.message,
                        ),
                    )
                    logger.error("Vips pipeline failed! Pipeline results: $appliedTransformations", e)
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
            processedPixelAccess = currentPixelAccess,
        )
    }

    private fun prepareForNextTransformation(
        arena: Arena,
        processed: VImage,
        isAlphaPremultiplied: Boolean,
        pixelAccess: PixelAccess,
        decision: TransformationDecision.Apply,
    ): PipelineState {
        var newAlphaState = isAlphaPremultiplied
        val alphaPrepared =
            when (decision.requiredAlpha) {
                AlphaRequirement.PREMULTIPLIED -> {
                    processed.premultiplyIfNecessary(isAlphaPremultiplied).let {
                        newAlphaState = it.second
                        it.first
                    }
                }
                AlphaRequirement.UN_PREMULTIPLIED -> {
                    processed.unPremultiplyIfNecessary(isAlphaPremultiplied).also {
                        newAlphaState = false
                    }
                }
                AlphaRequirement.EITHER -> processed
            }
        val requiresRandomAccess =
            pixelAccess == PixelAccess.SEQUENTIAL && decision.requiredPixelAccess == PixelAccess.RANDOM
        val preparedSource =
            if (requiresRandomAccess) {
                VipsImageCopyMemory.copyMemory(arena, alphaPrepared)
            } else {
                alphaPrepared
            }

        return PipelineState(
            isAlphaPremultiplied = newAlphaState,
            processed = preparedSource,
            currentPixelAccess = if (requiresRandomAccess) PixelAccess.RANDOM else pixelAccess,
        )
    }

    private data class PipelineState(
        val processed: VImage,
        val isAlphaPremultiplied: Boolean,
        val currentPixelAccess: PixelAccess,
    )
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
    val processedPixelAccess: PixelAccess,
)

data class AppliedTransformation(
    val name: String,
    val exceptionMessage: String?,
) {
    companion object Factory {
        fun success(name: String): AppliedTransformation =
            AppliedTransformation(
                name = name,
                exceptionMessage = null,
            )
    }

    override fun toString(): String =
        if (exceptionMessage == null) {
            "Successfully applied transformation $name"
        } else {
            "Failed transformation $name: $exceptionMessage"
        }
}
