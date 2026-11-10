package io.konifer.domain.asset

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EntryIdTest {
    @Test
    fun `accepts zero and maximum long IDs`() {
        EntryId(0).id shouldBe 0L
        EntryId(Long.MAX_VALUE).id shouldBe Long.MAX_VALUE
    }

    @Test
    fun `rejects negative IDs`() {
        shouldThrow<IllegalArgumentException> { EntryId(-1) }
        shouldThrow<IllegalArgumentException> { EntryId(Long.MIN_VALUE) }
    }

    @Test
    fun `renders IDs as numbers for URLs and messages`() {
        EntryId(42).toString() shouldBe "42"
        EntryId(0).toString() shouldBe "0"
        EntryId(Long.MAX_VALUE).toString() shouldBe Long.MAX_VALUE.toString()
    }
}
