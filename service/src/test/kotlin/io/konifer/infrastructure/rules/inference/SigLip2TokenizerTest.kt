package io.konifer.infrastructure.rules.inference

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigLip2TokenizerTest {
    private val tokenizer = Siglip2Tokenizer()

    @AfterAll
    fun afterAll() {
        tokenizer.close()
    }

    @Test
    fun `can tokenize a prompt`() {
        with(tokenizer.encodeBatch(listOf("Hello world")).single()) {
            inputIds shouldHaveSize 64
            attentionMask shouldHaveSize 64
            attentionMask.any { it == 1L } shouldBe true
        }
    }

    @Test
    fun `same prompt is tokenized the same way`() {
        tokenizer.encodeBatch(listOf("Hello world")) shouldBe tokenizer.encodeBatch(listOf("Hello world"))
    }

    @Test
    fun `can tokenize prompts in batch`() {
        val tokens = tokenizer.encodeBatch(listOf("Hello world", "Joshua tree"))

        tokens shouldHaveSize 2
        tokens.forEach {
            it.inputIds shouldHaveSize 64
            it.attentionMask shouldHaveSize 64
        }
    }

    @Test
    fun `lowercases the prompt`() {
        tokenizer.encodeBatch(listOf("Hello world")) shouldBe tokenizer.encodeBatch(listOf("HELLO WORLD"))
    }

    @Test
    fun `trims the prompt`() {
        tokenizer.encodeBatch(listOf("Hello world ")) shouldBe tokenizer.encodeBatch(listOf(" Hello world"))
    }
}
