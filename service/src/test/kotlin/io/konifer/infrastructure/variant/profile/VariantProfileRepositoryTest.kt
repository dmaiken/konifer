package io.konifer.infrastructure.variant.profile

import com.typesafe.config.ConfigFactory
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.createRequestedImageTransformation
import io.konifer.domain.transformation.InvalidTransformationException
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class VariantProfileRepositoryTest {
    companion object {
        @JvmStatic
        fun validProfilesSource(): List<Arguments> =
            listOf(
                Arguments.arguments(
                    """
                    variant-profiles {
                        small {
                            height = 10
                            width = 15
                            format = png
                        }
                    }
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
                    // ...arguments =
                    """
                    variant-profiles {
                        small {
                            height = 10
                            width = 15
                        }
                    }
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
                    variant-profiles {
                        small {
                            height = 10
                        }
                    }
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
                    variant-profiles {
                        small {
                            width = 15
                        }
                    }
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
                    variant-profiles {
                        small {
                            width = 15
                        }
                        medium {
                            height = 15
                        }
                    }
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
                    variant-profiles {
                        small {
                            width = 15
                            height = 10
                            fit = stretch
                            rotate = auto
                            filter = sepia
                        }
                        medium {
                            width = 15
                            height = 10
                            fit = fill
                            rotate = 180
                            flip = v
                            filter = black_white
                        }
                    }
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
                    variant-profiles {
                        "sma%ll" {
                            width = 15
                        }
                    }
                    """.trimIndent(),
                ),
                Arguments.arguments(
                    "sma/ll",
                    """
                    variant-profiles {
                        "sma/ll" {
                            width = 15
                        }
                    }
                    """.trimIndent(),
                ),
                Arguments.arguments(
                    "sma+ll",
                    """
                    variant-profiles {
                        "sma+ll" {
                            width = 15
                        }
                    }
                    """.trimIndent(),
                ),
                Arguments.arguments(
                    "sma=ll",
                    """
                    variant-profiles {
                        "sma=ll" {
                            width = 15
                        }
                    }
                    """.trimIndent(),
                ),
                Arguments.arguments(
                    "sma^ll",
                    """
                    variant-profiles {
                        "sma^ll" {
                            width = 15
                        }
                    }
                    """.trimIndent(),
                ),
                Arguments.arguments(
                    "sma&ll",
                    """
                    variant-profiles {
                        "sma&ll" {
                            width = 15
                        }
                    }
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
                        variant-profiles {
                            small {
                                width = 0
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad height",
                        """
                        variant-profiles {
                            small {
                                height = 0
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad format",
                        """
                        variant-profiles {
                            small {
                                format = bad
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad fit",
                        """
                        variant-profiles {
                            small {
                                fit = bad
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad rotate",
                        """
                        variant-profiles {
                            small {
                                rotate = bad
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad flip",
                        """
                        variant-profiles {
                            small {
                                flip = "bad"
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad filter",
                        """
                        variant-profiles {
                            small {
                                filter = bad
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad gravity",
                        """
                        variant-profiles {
                            small {
                                height = 10
                                width = 10
                                fit = crop
                                gravity = bad
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad pad",
                        """
                        variant-profiles {
                            small {
                                pad = bad
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad pad-color",
                        """
                        variant-profiles {
                            small {
                                pad-color = bad
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad strip",
                        """
                        variant-profiles {
                            small {
                                strip = bad
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                Arguments.arguments(
                    Named.named(
                        "bad color-space",
                        """
                        variant-profiles {
                            small {
                                color-space = bad
                            }
                        }
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
                ConfigFactory.parseString(config),
            )

        expectedProfiles.forEach { (name, profile) ->
            repository.fetch(name) shouldBe profile
        }
    }

    @Test
    fun `variant profile must have a name`() {
        val config =
            """
            variant-profiles {
                bad(name) {
                    width = 15
                }
            }
            """.trimIndent()

        shouldThrow<IllegalArgumentException> {
            ConfigurationVariantProfileRepository(
                ConfigFactory.parseString(config),
            )
        }.message shouldBe "Profile name: 'bad(name)' is not valid"
    }

    @ParameterizedTest
    @EnumSource(PixelLayout::class)
    fun `loads pixel channels and layout from profile configuration`(layout: PixelLayout) {
        val config =
            """
            variant-profiles {
                pixels {
                    format = pixels
                    pixel-channels = BGRA
                    pixel-layout = ${layout.queryParameterValue}
                }
            }
            """.trimIndent()
        val repository = ConfigurationVariantProfileRepository(ConfigFactory.parseString(config))

        repository.fetch("pixels") shouldBe
            RequestedTransformation(
                format = ImageFormat.PIXELS,
                pixelChannels = "BGRA".toPixelChannels(),
                pixelLayout = layout,
            )
    }

    @Test
    fun `unknown variant profile throws an invalid transformation`() {
        val config =
            """
            variant-profiles {
                small {
                    width = 15
                }
            }
            """.trimIndent()
        val repository =
            ConfigurationVariantProfileRepository(
                ConfigFactory.parseString(config),
            )
        val exception =
            shouldThrow<InvalidTransformationException> {
                repository.fetch("medium")
            }
        exception.message shouldBe "Variant profile: 'medium' not found"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "variant-profiles { }",
            "",
        ],
    )
    fun `does not throw when no variant profiles defined in config`(config: String) {
        shouldNotThrowAny {
            ConfigurationVariantProfileRepository(
                ConfigFactory.parseString(config),
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
                ConfigFactory.parseString(config),
            )
        }.message shouldBe "Profile name: '$profileName' is not valid"
    }

    @ParameterizedTest
    @MethodSource("invalidProfileSource")
    fun `cannot have invalid variant profile definitions`(config: String) {
        shouldThrowAny {
            ConfigurationVariantProfileRepository(
                ConfigFactory.parseString(config),
            )
        }
    }
}
