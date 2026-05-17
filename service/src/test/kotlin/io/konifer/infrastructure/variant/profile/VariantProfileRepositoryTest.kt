package io.konifer.infrastructure.variant.profile

import com.typesafe.config.ConfigFactory
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.createRequestedImageTransformation
import io.konifer.service.context.RequestedTransformation
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.server.config.HoconApplicationConfig
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class VariantProfileRepositoryTest {
    companion object {
        @JvmStatic
        fun validProfilesSource(): List<Arguments> =
            listOf(
                Arguments.arguments(
                    """
                    variant-profiles = [
                        {
                            name = small
                            h = 10
                            w = 15
                            format = png
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
                Arguments.arguments(
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
                Arguments.arguments(
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
                Arguments.arguments(
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
                Arguments.arguments(
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
                Arguments.arguments(
                    """
                    variant-profiles = [
                        {
                            name = small
                            w = 15
                            h = 10
                            fit = stretch
                            r = auto
                            filter = sepia
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
                                filter = Filter.SEPIA,
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
                Arguments.arguments(
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
                Arguments.arguments(
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
                Arguments.arguments(
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
                Arguments.arguments(
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
                Arguments.arguments(
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
                Arguments.arguments(
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
                Arguments.arguments(
                    Named.named(
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
                Arguments.arguments(
                    Named.named(
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
                Arguments.arguments(
                    Named.named(
                        "bad format",
                        """
                        variant-profiles = [
                            {
                                name = small
                                format = bad
                            }
                        ]
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
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
                Arguments.arguments(
                    Named.named(
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
                Arguments.arguments(
                    Named.named(
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
                Arguments.arguments(
                    Named.named(
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
                Arguments.arguments(
                    Named.named(
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
                Arguments.arguments(
                    Named.named(
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
                Arguments.arguments(
                    Named.named(
                        "bad pad-color",
                        """
                        variant-profiles = [
                            {
                                name = small
                                pad-c = bad
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
    ) {
        val repository =
            ConfigurationVariantProfileRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )

        expectedProfiles.forEach { (name, profile) ->
            repository.fetch(name) shouldBe profile
        }
    }

    @Test
    fun `variant profile must have a name`() {
        val config =
            """
            variant-profiles = [
                {
                    w = 15
                }
            ]
            """.trimIndent()

        shouldThrow<IllegalArgumentException> {
            ConfigurationVariantProfileRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        }.message shouldBe "All variant profiles must have a name"
    }

    @Test
    fun `null is returned when variant profile cannot be found`() {
        val config =
            """
            variant-profiles = [
                {
                    name = small
                    w = 15
                }
            ]
            """.trimIndent()
        val repository =
            ConfigurationVariantProfileRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val exception =
            shouldThrow<IllegalArgumentException> {
                repository.fetch("medium")
            }
        exception.message shouldBe "Variant profile: 'medium' not found"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "variant-profiles = [ ]",
            "",
        ],
    )
    fun `does not throw when no variant profiles defined in config`(config: String) {
        shouldNotThrowAny {
            ConfigurationVariantProfileRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        }
    }

    @ParameterizedTest
    @MethodSource("invalidVariantProfileNameSource")
    fun `cannot have invalid profile names`(
        profileName: String,
        config: String,
    ) {
        shouldThrow<IllegalArgumentException> {
            ConfigurationVariantProfileRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        }.message shouldBe "Profile name: '$profileName' is not valid"
    }

    @Test
    fun `cannot have duplicate profile names`() {
        val config =
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
            """.trimIndent()
        shouldThrow<IllegalArgumentException> {
            ConfigurationVariantProfileRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        }.message shouldBe "Profile name: 'small' already exists"
    }

    @ParameterizedTest
    @MethodSource("invalidProfileSource")
    fun `cannot have invalid variant profile definitions`(config: String) {
        shouldThrow<IllegalArgumentException> {
            ConfigurationVariantProfileRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        }
    }
}
