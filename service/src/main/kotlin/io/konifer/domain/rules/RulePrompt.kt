package io.konifer.domain.rules

@JvmInline
value class RulePrompt private constructor(
    val prompt: String,
) {
    init {
        if (prompt.isBlank()) {
            throw InvalidRuleDefinitionException("Prompt must not be empty.")
        }
        if (prompt.length > 256) {
            throw InvalidRuleDefinitionException("Prompt must not exceed 256 characters.")
        }
    }

    companion object {
        operator fun invoke(prompt: String): RulePrompt = RulePrompt(prompt.trim().lowercase())
    }
}
