package io.konifer.domain.context

import io.konifer.common.image.ManipulationParameters
import io.konifer.common.selector.ReturnFormat
import io.konifer.createRequestedImageTransformation
import io.konifer.domain.context.selector.QuerySelectors
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.variant.OnDemandVariantMode
import io.konifer.domain.variant.OnDemandVariantProperties
import io.konifer.domain.variant.TransformProperties
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class RequestContextValidatorTest {
    private val variantProfileRepository = mockk<VariantProfileRepository>()
    private val validator = RequestContextValidator(variantProfileRepository)

    @Test
    fun `metadata requests cannot specify image attributes`() {
        val exception =
            shouldThrow<InvalidPathException> {
                validator.validateFetchRequest(
                    pathConfiguration = pathConfiguration(),
                    querySelectors = QuerySelectors(returnFormat = ReturnFormat.INFO),
                    requestedTransformation = createRequestedImageTransformation(width = 100),
                    queryParameters = Parameters.Empty,
                )
            }

        exception.message shouldBe "Cannot specify image attributes when requesting asset metadata"
    }

    @Test
    fun `metadata requests allow the original variant`() {
        shouldNotThrowAny {
            validator.validateFetchRequest(
                pathConfiguration = pathConfiguration(mode = OnDemandVariantMode.DISABLED),
                querySelectors = QuerySelectors(returnFormat = ReturnFormat.INFO),
                requestedTransformation = RequestedTransformation.ORIGINAL_VARIANT,
                queryParameters = Parameters.Empty,
            )
        }
    }

    @ParameterizedTest
    @EnumSource(ReturnFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["INFO"])
    fun `non-metadata requests allow omitted transformations`(returnFormat: ReturnFormat) {
        shouldNotThrowAny {
            validator.validateFetchRequest(
                pathConfiguration = pathConfiguration(mode = OnDemandVariantMode.DISABLED),
                querySelectors = QuerySelectors(returnFormat = returnFormat),
                requestedTransformation = null,
                queryParameters = Parameters.Empty,
            )
        }
    }

    @ParameterizedTest
    @EnumSource(ReturnFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["INFO"])
    fun `non-metadata requests allow the original variant`(returnFormat: ReturnFormat) {
        shouldNotThrowAny {
            validator.validateFetchRequest(
                pathConfiguration = pathConfiguration(mode = OnDemandVariantMode.DISABLED),
                querySelectors = QuerySelectors(returnFormat = returnFormat),
                requestedTransformation = RequestedTransformation.ORIGINAL_VARIANT,
                queryParameters = Parameters.Empty,
            )
        }
    }

    @Test
    fun `enabled on-demand mode allows arbitrary transformations`() {
        shouldNotThrowAny {
            validator.validateFetchRequest(
                pathConfiguration = pathConfiguration(mode = OnDemandVariantMode.ENABLED),
                querySelectors = QuerySelectors(returnFormat = ReturnFormat.CONTENT),
                requestedTransformation = createRequestedImageTransformation(width = 100),
                queryParameters =
                    parameters {
                        append(ManipulationParameters.WIDTH, "100")
                    },
            )
        }
    }

    @Test
    fun `profile-only on-demand mode allows profile requests`() {
        shouldNotThrowAny {
            validator.validateFetchRequest(
                pathConfiguration = pathConfiguration(mode = OnDemandVariantMode.PROFILE_ONLY),
                querySelectors = QuerySelectors(returnFormat = ReturnFormat.CONTENT),
                requestedTransformation = createRequestedImageTransformation(width = 100),
                queryParameters =
                    parameters {
                        append(ManipulationParameters.VARIANT_PROFILE, "thumbnail")
                    },
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["phone-model", "label:w", "s"])
    fun `profile-only on-demand mode allows label and other query parameters`(labelKey: String) {
        shouldNotThrowAny {
            validator.validateFetchRequest(
                pathConfiguration = pathConfiguration(mode = OnDemandVariantMode.PROFILE_ONLY),
                querySelectors = QuerySelectors(returnFormat = ReturnFormat.CONTENT),
                requestedTransformation = createRequestedImageTransformation(width = 100),
                queryParameters =
                    parameters {
                        append(labelKey, "value")
                    },
            )
        }
    }

    @Test
    fun `profile-only on-demand mode rejects explicit transformation parameters`() {
        val exception =
            shouldThrow<IllegalRequestedTransformationException> {
                validator.validateFetchRequest(
                    pathConfiguration = pathConfiguration(mode = OnDemandVariantMode.PROFILE_ONLY),
                    querySelectors = QuerySelectors(returnFormat = ReturnFormat.CONTENT),
                    requestedTransformation = createRequestedImageTransformation(width = 100),
                    queryParameters =
                        parameters {
                            append(ManipulationParameters.WIDTH, "100")
                        },
                )
            }

        exception.message shouldBe "Only '${ManipulationParameters.VARIANT_PROFILE}' can be specified"
    }

    @Test
    fun `disabled on-demand mode allows transformations matching eager variants`() {
        val thumbnail = createRequestedImageTransformation(width = 100)
        val small = createRequestedImageTransformation(width = 200)
        every { variantProfileRepository.fetch("thumbnail") } returns thumbnail
        every { variantProfileRepository.fetch("small") } returns small

        shouldNotThrowAny {
            validator.validateFetchRequest(
                pathConfiguration =
                    pathConfiguration(
                        mode = OnDemandVariantMode.DISABLED,
                        eagerVariants = listOf("thumbnail", "small"),
                    ),
                querySelectors = QuerySelectors(returnFormat = ReturnFormat.CONTENT),
                requestedTransformation = small,
                queryParameters =
                    parameters {
                        append(ManipulationParameters.VARIANT_PROFILE, "small")
                    },
            )
        }
    }

    @Test
    fun `disabled on-demand mode rejects transformations that are not eager variants`() {
        every { variantProfileRepository.fetch("thumbnail") } returns createRequestedImageTransformation(width = 100)

        val exception =
            shouldThrow<IllegalRequestedTransformationException> {
                validator.validateFetchRequest(
                    pathConfiguration =
                        pathConfiguration(
                            mode = OnDemandVariantMode.DISABLED,
                            eagerVariants = listOf("thumbnail"),
                        ),
                    querySelectors = QuerySelectors(returnFormat = ReturnFormat.CONTENT),
                    requestedTransformation = createRequestedImageTransformation(width = 200),
                    queryParameters =
                        parameters {
                            append(ManipulationParameters.WIDTH, "200")
                        },
                )
            }

        exception.message shouldBe "Transformation not allowed"
    }

    private fun pathConfiguration(
        mode: OnDemandVariantMode = OnDemandVariantMode.ENABLED,
        eagerVariants: List<String> = emptyList(),
    ): PathConfiguration =
        PathConfiguration(
            transform =
                TransformProperties(
                    eagerVariants = eagerVariants,
                    onDemandVariant = OnDemandVariantProperties(mode = mode),
                ),
        )

    private fun parameters(block: ParametersBuilder.() -> Unit): Parameters =
        ParametersBuilder()
            .apply(block)
            .build()
}
