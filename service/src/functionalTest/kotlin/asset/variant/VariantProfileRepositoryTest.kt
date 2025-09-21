package asset.variant

import config.testInMemory
import image.model.ImageFormat
import image.model.RequestedImageTransformation
import io.asset.variant.VariantProfileRepository
import io.image.model.Fit
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
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
                            RequestedImageTransformation(
                                height = 10,
                                width = 15,
                                format = ImageFormat.PNG,
                                fit = Fit.SCALE,
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
                            RequestedImageTransformation(
                                height = 10,
                                width = 15,
                                format = null,
                                fit = Fit.SCALE,
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
                            RequestedImageTransformation(
                                height = 10,
                                width = null,
                                format = null,
                                fit = Fit.SCALE,
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
                            RequestedImageTransformation(
                                height = null,
                                width = 15,
                                format = null,
                                fit = Fit.SCALE,
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
                            RequestedImageTransformation(
                                height = null,
                                width = 15,
                                format = null,
                                fit = Fit.SCALE,
                            ),
                        "medium" to
                            RequestedImageTransformation(
                                height = 15,
                                width = null,
                                format = null,
                                fit = Fit.SCALE,
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
    }

    @ParameterizedTest
    @MethodSource("validProfilesSource")
    fun `can populate variant profiles`(
        config: String,
        expectedProfiles: Map<String, RequestedImageTransformation>,
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
}
