package io.konifer.infrastructure.objectstore.property

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TemplatePropertiesTest {
    @Test
    fun `can create properties and resolve properties`() {
        val properties =
            shouldNotThrowAny {
                TemplateProperties(
                    string = "https://something.{bucket}.com/{key}",
                )
            }

        properties.resolve("myBucket", "myKey") shouldBe "https://something.myBucket.com/myKey"
    }

    @Test
    fun `cannot use blank template string`() {
        shouldThrow<IllegalArgumentException> {
            TemplateProperties(
                string = " ",
            )
        }
    }

    @Test
    fun `template variables can be repeated in template`() {
        val properties =
            shouldNotThrowAny {
                TemplateProperties(
                    string = "https://something.{bucket}.{bucket}.com/{key}/{key}",
                )
            }

        properties.resolve("myBucket", "myKey") shouldBe "https://something.myBucket.myBucket.com/myKey/myKey"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "data:", "javascript:", "vbscript:",
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
