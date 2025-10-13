package io.image.vips.pipeline

import app.photofox.vipsffm.VImage
import io.image.model.Transformation
import io.image.vips.transformation.VipsTransformer
import io.ktor.util.logging.KtorSimpleLogger
import java.lang.foreign.Arena
import kotlin.time.Duration
import kotlin.time.measureTime

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
        transformation: Transformation?,
    ): VipsPipelineResult {
        if (transformation == null) {
            return VipsPipelineResult(
                successful = true,
                processed = source,
                requiresLqipRegeneration = false,
                appliedTransformations = emptyList(),
                executionTime = Duration.ZERO,
            )
        }
        val appliedTransformations = mutableListOf<AppliedTransformation>()
        var processed =
            VipsTransformationResult(
                processed = source,
                requiresLqipRegeneration = false,
            )
        var failed = false

        val executionTime =
            measureTime {
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
                        try {
                            processed =
                                transformer.transform(
                                    arena = arena,
                                    source = processed.processed,
                                    transformation = transformation,
                                )
                            appliedTransformations.add(
                                AppliedTransformation(
                                    name = transformer.name,
                                    exceptionMessage = null,
                                ),
                            )
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
            }

        if (!failed) {
            logger.info("Successfully processed image with transformation: $transformation with results: $appliedTransformations")
        }

        return VipsPipelineResult(
            successful = !failed,
            processed = processed.processed,
            requiresLqipRegeneration = processed.requiresLqipRegeneration,
            appliedTransformations = appliedTransformations,
            executionTime = executionTime,
        )
    }
}

fun vipsPipeline(initializer: VipsPipelineBuilder.() -> Unit): VipsPipelineBuilder {
    return VipsPipelineBuilder().apply(initializer)
}

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
    val executionTime: Duration,
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
