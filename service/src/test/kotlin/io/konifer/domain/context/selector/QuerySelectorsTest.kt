package io.konifer.domain.context.selector

import io.konifer.common.selector.Order
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.context.InvalidQuerySelectorsException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class QuerySelectorsTest {
    @ParameterizedTest
    @EnumSource(ReturnFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["ENTRIES"])
    fun `limit cannot be greater than one if format is not entries`(format: ReturnFormat) {
        shouldThrow<InvalidQuerySelectorsException> {
            QuerySelectors(
                returnFormat = format,
                limit = 2,
            )
        }.message shouldBe "Cannot have limit > 1 with return format of: ${format.name.lowercase()}"
    }

    @Test
    fun `limit can be greater than one if format is entries`() {
        shouldNotThrowAny {
            QuerySelectors(
                returnFormat = ReturnFormat.ENTRIES,
                limit = 2,
            ).apply {
                limit shouldBe 2
                returnFormat shouldBe ReturnFormat.ENTRIES
            }
        }
    }

    @Test
    fun `entries defaults to twenty while single-result formats default to one`() {
        QuerySelectors(returnFormat = ReturnFormat.ENTRIES).limit shouldBe 20
        QuerySelectors(returnFormat = ReturnFormat.INFO).limit shouldBe 1
        QuerySelectors().limit shouldBe 1
    }

    @Test
    fun `info cannot request all entries`() {
        shouldThrow<InvalidQuerySelectorsException> {
            QuerySelectors(returnFormat = ReturnFormat.INFO, limit = -1)
        }.message shouldBe "Info requests must select a single entry"
    }

    @Test
    fun `entries cannot select an explicit entry ID`() {
        shouldThrow<InvalidQuerySelectorsException> {
            QuerySelectors(returnFormat = ReturnFormat.ENTRIES, entryId = 42)
        }.message shouldBe "Entries cannot be selected by entry ID"
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
