package io.konifer.infrastructure.inference

import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.upload.UploadRule

data class DefinedUploadRule(
    val uploadRule: UploadRule,
    val ruleDefinition: RuleDefinition,
)
