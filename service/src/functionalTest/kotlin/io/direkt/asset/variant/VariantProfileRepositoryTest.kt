package io.direkt.asset.variant

import io.createRequestedImageTransformation
import io.direkt.config.testInMemory
import io.direkt.image.model.Filter
import io.direkt.image.model.Fit
import io.direkt.image.model.Flip
import io.direkt.image.model.ImageFormat
import io.direkt.image.model.RequestedTransformation
import io.direkt.image.model.Rotate
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class VariantProfileRepositoryTest {
    companion object {
        @JvmStatic
        fun validProfilesSource(): List<Arguments> =
            listOf(
                arguments(
                    """
                    variant-profiles = [
                        {
                            name = small
                            h = 10
                            w = 15
                            mimeType = "image/png"
                        }
                    ]
                    """.trimIndent(),
                    mapOf(
                        "small" to
                            createRequestedImageTransformation(
                                width = 15,
                                height = 10,
                                format = ImageFormat.PNG,
                            ),
                    ),
                ),
                arguments(
                    """
                    variant-profiles = [
                        {
                            name = small
                            h = 10
                            w = 15
                        }
                    ]
                    """.trimIndent(),
                    mapOf(
                        "small" to
                            createRequestedImageTransformation(
                                width = 15,
                                height = 10,
                            ),
                    ),
                ),
                arguments(
                    """
                    variant-profiles = [
                        {
                            name = small
                            h = 10
                        }
                    ]
                    """.trimIndent(),
                    mapOf(
                        "small" to
                            createRequestedImageTransformation(
                                height = 10,
                            ),
                    ),
                ),
                arguments(
                    """
                    variant-profiles = [
                        {
                            name = small
                            w = 15
                        }
                    ]
                    """.trimIndent(),
                    mapOf(
                        "small" to
                            createRequestedImageTransformation(
                                width = 15,
                            ),
                    ),
                ),
                arguments(
                    """
                    variant-profiles = [
                        {
                            name = small
                            w = 15
                        },
                        {
                            name = medium
                            h = 15
                        }
                    ]
                    """.trimIndent(),
                    mapOf(
                        "small" to
                            createRequestedImageTransformation(
                                width = 15,
                            ),
                        "medium" to
                            createRequestedImageTransformation(
                                height = 15,
                            ),
                    ),
                ),
                arguments(
                    """
                    variant-profiles = [
                        {
                            name = small
                            w = 15
                            h = 10
                            fit = stretch
                            r = auto
                            filter = greyscale
                        },
                        {
                            name = medium
                            w = 15
                            h = 10
                            fit = fill
                            r = 180
                            f = v
                            filter = black_white
                        }
                    ]
                    """.trimIndent(),
                    mapOf(
                        "small" to
                            createRequestedImageTransformation(
                                width = 15,
                                height = 10,
                                fit = Fit.STRETCH,
                                rotate = Rotate.AUTO,
                                filter = Filter.GREYSCALE,
                            ),
                        "medium" to
                            createRequestedImageTransformation(
                                width = 15,
                                height = 10,
                                fit = Fit.FILL,
                                rotate = Rotate.ONE_HUNDRED_EIGHTY,
                                flip = Flip.V,
                                filter = Filter.BLACK_WHITE,
                            ),
                    ),
                ),
            )

        @JvmStatic
        fun invalidVariantProfileNameSource(): List<Arguments> =
            listOf(
                arguments(
                    "sma%ll",
                    """
                    variant-profiles = [
                        {
                            name = "sma%ll"
                            w = 15
                        }
                    ]
                    """.trimIndent(),
                ),
                arguments(
                    "sma/ll",
                    """
                    variant-profiles = [
                        {
                            name = "sma/ll"
                            w = 15
                        }
                    ]
                    """.trimIndent(),
                ),
                arguments(
                    "sma+ll",
                    """
                    variant-profiles = [
                        {
                            name = "sma+ll"
                            w = 15
                        }
                    ]
                    """.trimIndent(),
                ),
                arguments(
                    "sma=ll",
                    """
                    variant-profiles = [
                        {
                            name = "sma=ll"
                            w = 15
                        }
                    ]
                    """.trimIndent(),
                ),
                arguments(
                    "sma^ll",
                    """
                    variant-profiles = [
                        {
                            name = "sma^ll"
                            w = 15
                        }
                    ]
                    """.trimIndent(),
                ),
                arguments(
                    "sma&ll",
                    """
                    variant-profiles = [
                        {
                            name = "sma&ll"
                            w = 15
                        }
                    ]
                    """.trimIndent(),
                ),
            )

        @JvmStatic
        fun invalidProfileSource() =
            listOf(
                arguments(
                    named(
                        "bad width",
                        """
                        variant-profiles = [
                            {
                                name = small
                                w = 0
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
                arguments(
                    named(
                        "bad height",
                        """
                        variant-profiles = [
                            {
                                name = small
                                h = 0
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
                arguments(
                    named(
                        "bad mimeType",
                        """
                        variant-profiles = [
                            {
                                name = small
                                mimeType = bad
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
                arguments(
                    named(
                        "bad fit",
                        """
                        variant-profiles = [
                            {
                                name = small
                                fit = bad
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
                arguments(
                    named(
                        "bad rotate",
                        """
                        variant-profiles = [
                            {
                                name = small
                                r = bad
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
                arguments(
                    named(
                        "bad flip",
                        """
                        variant-profiles = [
                            {
                                name = small
                                f = "bad"
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
                arguments(
                    named(
                        "bad filter",
                        """
                        variant-profiles = [
                            {
                                name = small
                                filter = bad
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
                arguments(
                    named(
                        "bad gravity",
                        """
                        variant-profiles = [
                            {
                                name = small
                                h = 10
                                w = 10
                                fit = crop
                                g = bad
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
                arguments(
                    named(
                        "bad pad",
                        """
                        variant-profiles = [
                            {
                                name = small
                                pad = bad
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
                arguments(
                    named(
                        "bad background",
                        """
                        variant-profiles = [
                            {
                                name = small
                                bg = bad
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
            )
    }

    @ParameterizedTest
    @MethodSource("validProfilesSource")
    fun `can populate variant profiles`(
        config: String,
        expectedProfiles: Map<String, RequestedTransformation>,
    ) = testInMemory(config) {
        application {
            val repository = VariantProfileRepository(environment.config)

            expectedProfiles.forEach { (name, profile) ->
                repository.fetch(name) shouldBe profile
            }
        }
    }

    @Test
    fun `variant profile must have a name`() =
        testInMemory(
            """
            variant-profiles = [
                {
                    w = 15
                }
            ]
            """.trimIndent(),
        ) {
            application {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        VariantProfileRepository(environment.config)
                    }
                exception.message shouldBe "All variant profiles must have a name"
            }
        }

    @Test
    fun `null is returned when variant profile cannot be found`() =
        testInMemory(
            """
            variant-profiles = [
                {
                    name = small
                    w = 15
                }
            ]
            """.trimIndent(),
        ) {
            application {
                val repository = VariantProfileRepository(environment.config)
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        repository.fetch("medium")
                    }
                exception.message shouldBe "Variant profile: 'medium' not found"
            }
        }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "variant-profiles = [ ]",
            "",
        ],
    )
    fun `does not throw when no variant profiles defined in config`(config: String) =
        testInMemory(config) {
            application {
                shouldNotThrowAny {
                    VariantProfileRepository(environment.config)
                }
            }
        }

    @ParameterizedTest
    @MethodSource("invalidVariantProfileNameSource")
    fun `cannot have invalid profile names`(
        profileName: String,
        config: String,
    ) = testInMemory(config) {
        application {
            val exception =
                shouldThrow<IllegalArgumentException> {
                    VariantProfileRepository(environment.config)
                }
            exception.message shouldBe "Profile name: '$profileName' is not valid"
        }
    }

    @Test
    fun `cannot have duplicate profile names`() =
        testInMemory(
            """
            variant-profiles = [
                {
                    name = small
                    w = 15
                },
                {
                    name = small
                    h = 15
                }
            ]
            """.trimIndent(),
        ) {
            application {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        VariantProfileRepository(environment.config)
                    }
                exception.message shouldBe "Profile name: 'small' already exists"
            }
        }

    @ParameterizedTest
    @MethodSource("invalidProfileSource")
    fun `cannot have invalid variant profile definitions`(config: String) =
        testInMemory(config) {
            application {
                shouldThrow<IllegalArgumentException> {
                    VariantProfileRepository(environment.config)
                }
            }
        }
}
