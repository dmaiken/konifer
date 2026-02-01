package io.konifer.service.context.selector

import io.konifer.service.context.InvalidQuerySelectorsException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class QuerySelectorsTest {
    @ParameterizedTest
    @EnumSource(ReturnFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["METADATA"])
    fun `limit cannot be greater than one if format is not metadata`(format: ReturnFormat) {
        shouldThrow<InvalidQuerySelectorsException> {
            QuerySelectors(
                returnFormat = format,
                limit = 2,
            )
        }.message shouldBe "Cannot have limit > 1 with return format of: ${format.name.lowercase()}"
    }

    @Test
    fun `limit can be greater than one if format is metadata`() {
        shouldNotThrowAny {
            QuerySelectors(
                returnFormat = ReturnFormat.METADATA,
                limit = 2,
            ).apply {
                limit shouldBe 2
                returnFormat shouldBe ReturnFormat.METADATA
            }
        }
    }

    @Test
    fun `defaults are used`() {
        val selectors = QuerySelectors()
        selectors.limit shouldBe 1
        selectors.returnFormat shouldBe ReturnFormat.LINK
        selectors.entryId shouldBe null
        selectors.order shouldBe Order.NEW
    }
}
