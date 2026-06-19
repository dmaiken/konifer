package io.konifer.domain.rules

@JvmInline
value class RuleName(val value: String) {
    init {
        require(value.isNotBlank()) { "Rule name cannot be blank" }
        require(value.length <= 32) { "Rule name cannot be longer than 32 characters" }
    }
}
