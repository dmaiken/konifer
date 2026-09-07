package io.konifer.infrastructure

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.ktor.http.Url
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HttpPropertiesTest {
    @Test
    fun `public url is optional`() {
        shouldNotThrowAny { HttpProperties() }
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://localhost:8080", "https://images.example.com"])
    fun `public url supports http protocols`(url: String) {
        shouldNotThrowAny { HttpProperties(publicUrl = Url(url)) }
    }

    @Test
    fun `public url rejects other protocols`() {
        shouldThrow<IllegalArgumentException> {
            HttpProperties(publicUrl = Url("ftp://images.example.com"))
        }
    }
}
