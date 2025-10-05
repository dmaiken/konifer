package io.image.vips

import io.image.model.Fit
import io.image.model.Gravity
import io.image.vips.transformation.Resize

object VipsPipelines {
    val lqipVariantPipeline =
        vipsPipeline {
            checkIfLqipRegenerationNeeded = false
            add(
                Resize(
                    width = 32,
                    height = 32,
                    fit = Fit.SCALE,
                    upscale = false,
                    gravity = Gravity.CENTER,
                ),
            )
        }.build()
}
