package io.konifer.domain.context

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.TransformableColorSpace
import io.konifer.common.selector.ReturnFormat
import io.konifer.createRequestedImageTransformation
import io.konifer.domain.context.selector.QuerySelectors
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.domain.transformation.InvalidTransformationException
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.TransformationNormalizer
import io.konifer.domain.transformation.forDelivery
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.OnDemandVariantMode
import io.konifer.domain.variant.OnDemandVariantProperties
import io.konifer.domain.variant.TransformProperties
import io.konifer.domain.variant.TransformationLimitProperties
import io.konifer.domain.variant.attribute.Attributes
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.Parameters
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class RequestContextValidatorTest {
    private val repository = mockk<VariantProfileRepository>()
    private val normalizer = TransformationNormalizer()
    private val validator = RequestContextValidator(repository, normalizer)
    private val original =
        Attributes(
            width = 200.toDimension(),
            height = 100.toDimension(),
            format = ImageFormat.PNG,
            colorSpace = ColorSpace.SRGB,
        )

    @ParameterizedTest
    @EnumSource(ReturnFormat::class, names = ["INFO", "ENTRIES"])
    fun `metadata requests reject transformations`(returnFormat: ReturnFormat) {
        shouldThrow<InvalidPathException> {
            validator.validateFetchRequest(QuerySelectors(returnFormat = returnFormat), createRequestedImageTransformation(width = 100))
        }.message shouldBe "Cannot specify image attributes when requesting asset metadata"
        shouldNotThrowAny { validator.validateFetchRequest(QuerySelectors(returnFormat = returnFormat), null) }
    }

    @Test
    fun `profile-only accepts expanded equivalent parameters and pinned negotiated format`() {
        val profile = createRequestedImageTransformation(width = 100)
        every { repository.fetch("thumbnail") } returns profile
        val requested = profile.copy(format = ImageFormat.WEBP)
        val resolved = normalizer.resolve(requested, original)
        val expanded = requested.forDelivery(resolved)
        shouldNotThrowAny {
            validator.validateDelivery(
                context(OnDemandVariantMode.PROFILE_ONLY, expanded, "thumbnail"),
                original,
                normalizer.resolve(expanded, original),
            )
        }
    }

    @Test
    fun `profile-only rejects parameters that change profile dimensions or rendering flags`() {
        val profile = createRequestedImageTransformation(width = 100)
        every { repository.fetch("thumbnail") } returns profile
        listOf(
            profile.copy(width = 101.toDimension()),
            profile.copy(colorSpace = TransformableColorSpace.SRGB),
        ).forEach { requested ->
            shouldThrow<IllegalRequestedTransformationException> {
                validator.validateDelivery(
                    context(OnDemandVariantMode.PROFILE_ONLY, requested, "thumbnail"),
                    original,
                    normalizer.resolve(requested, original),
                )
            }.message shouldBe "Transformation must match the selected profile"
        }
    }

    @Test
    fun `profile-only rejects format overrides when profile pins format`() {
        every { repository.fetch("thumbnail") } returns createRequestedImageTransformation(width = 100, format = ImageFormat.PNG)
        val requested = createRequestedImageTransformation(width = 100, format = ImageFormat.WEBP)
        shouldThrow<IllegalRequestedTransformationException> {
            validator.validateDelivery(
                context(OnDemandVariantMode.PROFILE_ONLY, requested, "thumbnail"),
                original,
                normalizer.resolve(requested, original),
            )
        }
    }

    @Test
    fun `profile-only requires a profile even if dimensions match one`() {
        val requested = createRequestedImageTransformation(width = 100)
        shouldThrow<IllegalRequestedTransformationException> {
            validator.validateDelivery(
                context(OnDemandVariantMode.PROFILE_ONLY, requested),
                original,
                normalizer.resolve(requested, original),
            )
        }.message shouldBe "A variant profile is required"
    }

    @Test
    fun `disabled mode accepts normalized eager transformations but rejects others`() {
        val profile = createRequestedImageTransformation(width = 100)
        every { repository.fetch("thumbnail") } returns profile
        val resolved = normalizer.resolve(profile, original)
        val expanded = profile.forDelivery(resolved)
        shouldNotThrowAny {
            validator.validateDelivery(context(OnDemandVariantMode.DISABLED, expanded), original, normalizer.resolve(expanded, original))
        }
        val changed = expanded.copy(width = 101.toDimension())
        shouldThrow<IllegalRequestedTransformationException> {
            validator.validateDelivery(context(OnDemandVariantMode.DISABLED, changed), original, normalizer.resolve(changed, original))
        }.message shouldBe "Transformation not allowed"
    }

    @ParameterizedTest
    @EnumSource(OnDemandVariantMode::class)
    fun `original content bypasses generation policy and limits`(mode: OnDemandVariantMode) {
        val resolved = Transformation.fromAttributes(original)
        val expanded = RequestedTransformation().forDelivery(resolved)
        shouldNotThrowAny {
            validator.validateDelivery(context(mode, expanded, maxWidth = 10), original, normalizer.resolve(expanded, original))
        }
    }

    @Test
    fun `resolved transformed content always obeys current limits`() {
        val requested = createRequestedImageTransformation(width = 100)
        shouldThrow<InvalidTransformationException> {
            validator.validateDelivery(
                context(OnDemandVariantMode.ENABLED, requested, maxWidth = 10),
                original,
                normalizer.resolve(requested, original),
            )
        }
    }

    private fun context(
        mode: OnDemandVariantMode,
        requested: RequestedTransformation,
        profile: String? = null,
        maxWidth: Int = 8192,
    ) = QueryRequestContext(
        path = "profile",
        pathConfiguration =
            PathConfiguration(
                transform =
                    TransformProperties(
                        eagerVariants = listOf("thumbnail"),
                        onDemandVariant = OnDemandVariantProperties(mode = mode),
                        limits = TransformationLimitProperties(maxWidth = maxWidth.toDimension()),
                    ),
            ),
        selectors = QuerySelectors(returnFormat = ReturnFormat.CONTENT),
        requestedTransformation = requested,
        labels = emptyMap(),
        request = HttpRequest(Parameters.build { profile?.let { append("profile", it) } }, mockk()),
    )
}
