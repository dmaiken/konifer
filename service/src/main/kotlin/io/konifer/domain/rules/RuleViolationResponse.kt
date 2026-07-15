package io.konifer.domain.rules

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class RuleViolationResponse(
    val value: String,
) {
    init {
        require(value.length < 200) { "Rule violation response cannot exceed 200 characters" }
    }
}
