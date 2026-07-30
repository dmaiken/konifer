package io.konifer.domain.rules

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RulePromptTest {
    @Test
    fun `constructor trims and lowercases prompt`() {
        RulePrompt("  A Joshua Tree  ").prompt shouldBe "a joshua tree"
    }

    @Test
    fun `constructor rejects blank prompt after trimming`() {
        shouldThrow<IllegalArgumentException> {
            RulePrompt("   ")
        }.message shouldBe "Prompt must not be empty."
    }

    @Test
    fun `prompts with the same normalized value are equal`() {
        RulePrompt("DOG") shouldBe RulePrompt(" dog ")
    }

    @Test
    fun `prompts cannot exceed maximum character limit`() {
        shouldThrow<IllegalArgumentException> {
            RulePrompt("a".repeat(257))
        }.message shouldBe "Prompt must not exceed 256 characters."
    }
}
