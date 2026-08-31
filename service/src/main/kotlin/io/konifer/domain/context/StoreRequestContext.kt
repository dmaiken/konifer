package io.konifer.domain.context

import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.rules.upload.DefaultRuleAction

data class StoreRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
) {
    /**
     * Does the asset require preprocessing? This is false only when processing is disabled within
     * path configuration and there are no lqips to generate.
     */
    fun requiresPreProcessing(): Boolean =
        pathConfiguration.transform.preProcessing.enabled ||
            pathConfiguration.lqip.isNotEmpty() ||
            pathConfiguration.uploadRuleset.default == DefaultRuleAction.REJECT ||
            pathConfiguration.uploadRuleset.requiresEvaluationBeyondDefault()
}
