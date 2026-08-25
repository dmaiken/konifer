package io.konifer.domain.transformation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TransformationParametersTest {
    @ParameterizedTest
    @ValueSource(ints = [1, Int.MAX_VALUE])
    fun `dimension must be positive`(value: Int) {
        value.toDimension().value shouldBe value
    }

    @ParameterizedTest
    @ValueSource(ints = [Int.MIN_VALUE, -1, 0])
    fun `dimension rejects non-positive values`(value: Int) {
        shouldThrow<IllegalArgumentException> {
            value.toDimension()
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 150])
    fun `blur accepts values between zero and one hundred fifty`(value: Int) {
        value.toBlur().value shouldBe value
    }

    @ParameterizedTest
    @ValueSource(ints = [Int.MIN_VALUE, -1, 151, Int.MAX_VALUE])
    fun `blur rejects values outside its bounds`(value: Int) {
        shouldThrow<IllegalArgumentException> {
            value.toBlur()
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 50, 100])
    fun `quality accepts values between one and one hundred`(value: Int) {
        value.toQuality().value shouldBe value
    }

    @ParameterizedTest
    @ValueSource(ints = [Int.MIN_VALUE, 0, 101, Int.MAX_VALUE])
    fun `quality rejects values outside its bounds`(value: Int) {
        shouldThrow<IllegalArgumentException> {
            value.toQuality()
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, Int.MAX_VALUE])
    fun `padding amount accepts non-negative values`(value: Int) {
        value.toPaddingAmount().value shouldBe value
    }

    @ParameterizedTest
    @ValueSource(ints = [Int.MIN_VALUE, -1])
    fun `padding amount rejects negative values`(value: Int) {
        shouldThrow<IllegalArgumentException> {
            value.toPaddingAmount()
        }
    }
}
