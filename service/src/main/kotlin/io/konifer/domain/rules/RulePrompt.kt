package io.konifer.domain.rules

@JvmInline
value class RulePrompt private constructor(
    val prompt: String,
) {
    init {
        require(prompt.isNotBlank()) { "Prompt must not be empty." }
        require(prompt.length <= 256) { "Prompt must not exceed 256 characters." }
    }

    companion object {
        operator fun invoke(prompt: String): RulePrompt = RulePrompt(prompt.trim().lowercase())
    }
}
