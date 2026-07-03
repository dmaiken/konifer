package io.konifer.domain.rules

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class RuleNameTest {
    @Test
    fun `constructor lowercases value`() {
        RuleName("DOGS ONLY").value shouldBe "dogs only"
    }

    @Test
    fun `deserialization lowercases value`() {
        Json.decodeFromString<RuleName>("\"DOGS ONLY\"").value shouldBe "dogs only"
    }

    @Test
    fun `serialization writes lowercase value`() {
        Json.encodeToString(RuleName("DOGS ONLY")) shouldBe "\"dogs only\""
    }
}
