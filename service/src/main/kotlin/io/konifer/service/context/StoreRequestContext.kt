package io.konifer.service.context

import io.konifer.domain.path.PathConfiguration

data class StoreRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
) {
    /**
     * Does the asset require preprocessing? This is false only when processing is disabled within
     * path configuration and there are no lqips to generate.
     */
    fun requiresPreProcessing(): Boolean = pathConfiguration.preProcessing.enabled || pathConfiguration.image.previews.isNotEmpty()
}
