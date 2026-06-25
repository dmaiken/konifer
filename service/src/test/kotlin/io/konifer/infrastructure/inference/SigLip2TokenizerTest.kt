package io.konifer.infrastructure.inference

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SigLip2TokenizerTest {
    private val tokenizer = Siglip2Tokenizer()

    @Test
    fun `can tokenize a prompt`() {
        with(tokenizer.encode("Hello world")) {
            inputIds shouldHaveSize 64
            attentionMask shouldHaveSize 64
            attentionMask.any { it == 1L } shouldBe true
        }
    }

    @Test
    fun `same prompt is tokenized the same way`() {
        tokenizer.encode("Hello world") shouldBe tokenizer.encode("Hello world")
    }

    @Test
    fun `lowercases the prompt`() {
        tokenizer.encode("Hello world") shouldBe tokenizer.encode("HELLO WORLD")
    }

    @Test
    fun `trims the prompt`() {
        tokenizer.encode("Hello world ") shouldBe tokenizer.encode(" Hello world")
    }
}
