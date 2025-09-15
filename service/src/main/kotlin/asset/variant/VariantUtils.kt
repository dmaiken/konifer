package io.asset.variant

import image.model.RequestedImageTransformation
import io.image.model.Fit

object VariantUtils {

    fun requiresOriginalToQueryVariant(requested: RequestedImageTransformation): Boolean {
        if (requested.isOriginalVariant()) {
            return false
        }

        if (requested.format != null) {
            return true
        }

        return requested.fit == Fit.SCALE && (requested.width != null || requested.height != null)
    }
}