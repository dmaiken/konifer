package io.image.vips

import app.photofox.vipsffm.VImage
import io.image.vips.transformation.VipsTransformer

class VipsPipelineBuilder {

    private val transformers = mutableListOf<VipsTransformer>()

    var checkIfLqipRegenerationNeeded = false

    fun add(transformer: VipsTransformer) {
        transformers.add(transformer)
    }

    fun build(): VipsPipeline = VipsPipeline(transformers, checkIfLqipRegenerationNeeded)
}

class VipsPipeline(
    private val transformers: List<VipsTransformer>,
    private val checkIfLqipRegenerationNeeded: Boolean,
) {
    fun run(source: VImage): VipsPipelineResult {
        var processed = source
        var requiresLqipRegeneration = false
        transformers.forEach { transformer ->
            processed = transformer.transform(processed)
            requiresLqipRegeneration = checkIfLqipRegenerationNeeded && (requiresLqipRegeneration || transformer.requiresLqipRegeneration(processed))
        }

        return VipsPipelineResult(
            processed = processed,
            requiresLqipRegeneration = requiresLqipRegeneration,
        )
    }
}

fun vipsPipeline(initializer: VipsPipelineBuilder.() -> Unit): VipsPipelineBuilder {
    return VipsPipelineBuilder().apply(initializer)
}

data class VipsPipelineResult(
    val processed: VImage,

    /**
     * If [VipsPipelineBuilder.checkIfLqipRegenerationNeeded] is false, then this will always be false.
     * Otherwise, if true, a new LQIP(s) will need to be generated for the [processed] image.
     */
    val requiresLqipRegeneration: Boolean,
)