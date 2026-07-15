package io.konifer.domain.asset

import io.konifer.domain.rules.RuleViolationResponse

class AssetRejectedException(
    violationResponses: List<RuleViolationResponse>,
) : IllegalArgumentException(
        violationResponses
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "; ") { it.value }
            ?: "Asset rejected by upload rules",
    )
