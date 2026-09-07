package io.konifer.domain.path

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TemplatePropertiesTest {
    @Test
    fun `cannot use blank template string`() {
        shouldThrow<IllegalArgumentException> {
            TemplateProperties(
                string = " ",
            )
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "data:", "javascript:", "vbscript:", "JAVASCRIPT:",
        ],
    )
    fun `template cannot use disallowed protocol`(protocol: String) {
        shouldThrow<IllegalArgumentException> {
            TemplateProperties(
                string = "$protocol//something",
            )
        }
    }
}
