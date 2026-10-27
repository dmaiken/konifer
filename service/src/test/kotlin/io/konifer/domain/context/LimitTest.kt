package io.konifer.domain.context

import io.konifer.domain.context.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LimitTest {
    @ParameterizedTest
    @ValueSource(ints = [1, 20, Int.MAX_VALUE, -1])
    fun `accepts positive and unlimited limits`(value: Int) {
        value.toLimit().value shouldBe value
        value.toString().toLimit() shouldBe value.toLimit()
        value.toLimit().isUnlimited shouldBe (value == -1)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -2, Int.MIN_VALUE])
    fun `rejects invalid numeric limits`(value: Int) {
        shouldThrow<InvalidRequestException> { Limit(value) }
        shouldThrow<InvalidRequestException> { value.toString().toLimit() }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "abc", "1.5", "2147483648", "-2147483649"])
    fun `rejects malformed and overflowing limits`(value: String) {
        shouldThrow<InvalidRequestException> { value.toLimit() }
    }

    @Test
    fun `named limits express single and unlimited selection`() {
        Limit.single.value shouldBe 1
        Limit.unlimited.value shouldBe -1
    }
}
