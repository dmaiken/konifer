package io.konifer.domain.rules

@JvmInline
value class RulePrompt private constructor(
    val prompt: String,
) {
    init {
        require(prompt.isNotBlank()) { "Prompt must not be empty." }
    }

    companion object {
        operator fun invoke(prompt: String): RulePrompt = RulePrompt(prompt.trim().lowercase())
    }
}
