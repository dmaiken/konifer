package io.konifer.domain.context.selector

import io.konifer.common.selector.Order
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.context.InvalidQuerySelectorsException
import io.konifer.domain.context.toLimit
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
                limit = 2.toLimit(),
            )
        }.message shouldBe "Limit must be 1 with return format of: ${format.name.lowercase()}"
    }

    @Test
    fun `limit can be greater than one if format is entries`() {
        shouldNotThrowAny {
            QuerySelectors(
                returnFormat = ReturnFormat.ENTRIES,
                limit = 2.toLimit(),
            ).apply {
                limit.value shouldBe 2
                returnFormat shouldBe ReturnFormat.ENTRIES
            }
        }
    }

    @Test
    fun `entries defaults to twenty while single-result formats default to one`() {
        QuerySelectors(returnFormat = ReturnFormat.ENTRIES).limit.value shouldBe 20
        QuerySelectors(returnFormat = ReturnFormat.INFO).limit.value shouldBe 1
        QuerySelectors().limit.value shouldBe 1
    }

    @Test
    fun `info cannot request all entries`() {
        shouldThrow<InvalidQuerySelectorsException> {
            QuerySelectors(returnFormat = ReturnFormat.INFO, limit = (-1).toLimit())
        }.message shouldBe "Limit must be 1 with return format of: info"
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
        selectors.limit.value shouldBe 1
        selectors.returnFormat shouldBe ReturnFormat.LINK
        selectors.entryId shouldBe null
        selectors.order shouldBe Order.NEW
    }
}
