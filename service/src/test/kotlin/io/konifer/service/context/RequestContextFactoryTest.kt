package io.konifer.service.context

import io.konifer.BaseUnitTest
import io.konifer.createRequestedImageTransformation
import io.konifer.domain.image.Fit
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.ImageProperties
import io.konifer.domain.path.CacheControlProperties
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.preprocessing.PreProcessingProperties
import io.konifer.infrastructure.objectstore.property.ObjectStoreProperties
import io.konifer.infrastructure.path.TriePathConfigurationRepository
import io.konifer.infrastructure.variant.profile.ConfigurationVariantProfileRepository
import io.konifer.service.context.selector.DeleteModifiers
import io.konifer.service.context.selector.Order
import io.konifer.service.context.selector.QuerySelectors
import io.konifer.service.context.selector.ReturnFormat
import io.konifer.service.context.selector.SpecifiedInRequest
import io.konifer.service.transformation.TransformationNormalizer
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class RequestContextFactoryTest : BaseUnitTest() {
    companion object {
        @JvmStatic
        fun queryModifierSource(): List<Arguments> =
            listOf(
                arguments(
                    "/assets/profile/-/new/metadata",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.METADATA,
                        order = Order.NEW,
                        limit = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/modified/metadata",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.METADATA,
                        order = Order.MODIFIED,
                        limit = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/redirect",
                    ParametersBuilder()
                        .apply {
                            append("limit", "1")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.REDIRECT,
                        order = Order.NEW,
                        limit = 1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/content",
                    ParametersBuilder()
                        .apply {
                            append("limit", "1")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.CONTENT,
                        order = Order.NEW,
                        limit = 1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/mEtAData/",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.METADATA,
                        order = Order.NEW,
                        limit = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/metadata",
                    ParametersBuilder()
                        .apply {
                            append("limit", "-1")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.METADATA,
                        order = Order.NEW,
                        limit = -1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/metadata",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.METADATA,
                        order = Order.NEW,
                        limit = 1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.METADATA,
                        order = Order.NEW,
                        limit = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata",
                    ParametersBuilder()
                        .apply {
                            append("limit", "-1")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.METADATA,
                        order = Order.NEW,
                        limit = -1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile",
                    ParametersBuilder()
                        .apply {
                            append("limit", "-1")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        order = Order.NEW,
                        limit = -1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.METADATA,
                        order = Order.NEW,
                        limit = 1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        order = Order.NEW,
                        limit = 1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                orderBy = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        order = Order.NEW,
                        limit = 1,
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile/-",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        order = Order.NEW,
                        limit = 1,
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        order = Order.NEW,
                        limit = 1,
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile/",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        order = Order.NEW,
                        limit = 1,
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/metadata",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.METADATA,
                        order = Order.NEW,
                        limit = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
            )

        @JvmStatic
        fun deleteModifierSource(): List<Arguments> =
            listOf(
                arguments(
                    "/assets/profile/-/new",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    DeleteModifiers(
                        order = Order.NEW,
                        limit = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/modified",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    DeleteModifiers(
                        order = Order.MODIFIED,
                        limit = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/new",
                    ParametersBuilder()
                        .apply {
                            append("limit", "-1")
                        }.build(),
                    DeleteModifiers(
                        order = Order.NEW,
                        limit = -1,
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/",
                    Parameters.Empty,
                    DeleteModifiers(
                        order = Order.NEW,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/-/modified/",
                    Parameters.Empty,
                    DeleteModifiers(
                        order = Order.MODIFIED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/",
                    Parameters.Empty,
                    DeleteModifiers(
                        order = Order.NEW,
                        limit = 1,
                        recursive = false,
                    ),
                ),
                arguments(
                    "/assets/profile/-/",
                    Parameters.Empty,
                    DeleteModifiers(
                        order = Order.NEW,
                        limit = 1,
                        recursive = false,
                    ),
                ),
                arguments(
                    "/assets/profile/-/recursive",
                    Parameters.Empty,
                    DeleteModifiers(
                        recursive = true,
                    ),
                ),
                arguments(
                    "/assets/profile/-/",
                    ParametersBuilder()
                        .apply {
                            append("limit", "-1")
                        }.build(),
                    DeleteModifiers(
                        limit = -1,
                    ),
                ),
            )

        @JvmStatic
        fun getEntryIdSource(): List<Arguments> =
            listOf(
                arguments(
                    "/assets/profile/-/entry/10/metadata",
                    QuerySelectors(
                        returnFormat = ReturnFormat.METADATA,
                        entryId = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/entry/10/content",
                    QuerySelectors(
                        returnFormat = ReturnFormat.CONTENT,
                        entryId = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/entry/10/redirect",
                    QuerySelectors(
                        returnFormat = ReturnFormat.REDIRECT,
                        entryId = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/entry/10/link",
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        entryId = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/entry/10",
                    QuerySelectors(
                        entryId = 10,
                    ),
                ),
            )

        @JvmStatic
        fun transformationSource(): List<Arguments> =
            listOf(
                arguments(
                    ParametersBuilder(3)
                        .apply {
                            append("w", "10")
                            append("h", "20")
                            append("format", "png")
                        }.build(),
                    Transformation(
                        width = 10,
                        height = 20,
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                    ),
                ),
                arguments(
                    ParametersBuilder(2)
                        .apply {
                            append("w", "10")
                            append("h", "20")
                        }.build(),
                    Transformation(
                        width = 10,
                        height = 20,
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                    ),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("w", "10")
                        }.build(),
                    Transformation(
                        width = 10,
                        height = 10,
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                    ),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("format", "jpg")
                        }.build(),
                    Transformation(
                        width = 100,
                        height = 100,
                        format = ImageFormat.JPEG,
                        fit = Fit.FIT,
                    ),
                ),
            )
    }

    private val pathConfigurationRepository = mockk<TriePathConfigurationRepository>()
    private val variantProfileRepository = mockk<ConfigurationVariantProfileRepository>()
    private val transformationNormalizer = TransformationNormalizer(assetRepository)
    private val requestContextFactory =
        RequestContextFactory(pathConfigurationRepository, variantProfileRepository, transformationNormalizer)

    @BeforeEach
    fun beforeEach() {
        every {
            pathConfigurationRepository.fetch(any())
        } returns PathConfiguration.DEFAULT
    }

    @Nested
    inner class GetRequestContextTests {
        @ParameterizedTest
        @MethodSource("io.konifer.service.context.RequestContextFactoryTest#queryModifierSource")
        fun `can fetch GET request context with query modifiers`(
            path: String,
            queryParameters: Parameters,
            expectedQuerySelectors: QuerySelectors,
        ) = runTest {
            val context =
                requestContextFactory.fromGetRequest(
                    path = path,
                    headers = HeadersBuilder().build(),
                    queryParameters = queryParameters,
                )

            context.pathConfiguration shouldBe PathConfiguration.DEFAULT
            context.selectors shouldBe expectedQuerySelectors
            context.labels shouldBe emptyMap()
        }

        @ParameterizedTest
        @MethodSource("io.konifer.service.context.RequestContextFactoryTest#getEntryIdSource")
        fun `can fetch GET request context with entryId`(
            path: String,
            expectedQuerySelectors: QuerySelectors,
        ) = runTest {
            val context =
                requestContextFactory.fromGetRequest(
                    path = path,
                    headers = HeadersBuilder().build(),
                    queryParameters = Parameters.Empty,
                )

            context.pathConfiguration shouldBe PathConfiguration.DEFAULT
            context.selectors shouldBe expectedQuerySelectors
            context.labels shouldBe emptyMap()
        }

        @Test
        fun `if variant profile is supplied then it is used to populate the requested image attributes`() =
            runTest {
                val profileName = "small"
                val variantConfig =
                    createRequestedImageTransformation(
                        width = 10,
                        height = 10,
                        format = ImageFormat.PNG,
                    )
                every {
                    variantProfileRepository.fetch(profileName)
                } returns variantConfig

                val context =
                    requestContextFactory.fromGetRequest(
                        path = "/assets/user/",
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder(1)
                                .apply {
                                    append("profile", profileName)
                                }.build(),
                    )

                context.pathConfiguration shouldBe PathConfiguration.DEFAULT
                context.transformation shouldBe
                    Transformation(
                        height = variantConfig.height!!,
                        width = variantConfig.width!!,
                        format = variantConfig.format!!,
                        fit = variantConfig.fit,
                    )
                context.labels shouldBe emptyMap()
            }

        @Test
        fun `specified image attributes override variant profile if supplied`() =
            runTest {
                val profileName = "small"
                every {
                    variantProfileRepository.fetch(profileName)
                } returns
                    createRequestedImageTransformation(
                        width = 10,
                        height = 20,
                        format = ImageFormat.PNG,
                    )
                val context =
                    requestContextFactory.fromGetRequest(
                        path = "/assets/user/",
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder(4)
                                .apply {
                                    append("profile", profileName)
                                    append("h", "100")
                                    append("w", "500")
                                    append("format", "jpg")
                                }.build(),
                    )

                context.pathConfiguration shouldBe PathConfiguration.DEFAULT
                context.transformation?.height shouldBe 100
                context.transformation?.width shouldBe 500
                context.transformation?.format shouldBe ImageFormat.JPEG
                context.labels shouldBe emptyMap()
            }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/assets/profile/-/new/metadata/10/",
                "/assets/profile/-/metadataa/new/10/",
                "/assets/profile/-/metadata/new/-1/",
                "/assets/profile/-/metadata/neww/10/",
                "/assets/profile/-/metadata/new/0/",
                "/assets/profile/-/10/metadata/new/",
                "/assets/profile/-/metadata/metadata/metadata/",
                "/assets/profile/-/new/new/new/",
                "/assets/profile/-/10/10/10/",
                "/assets/profile/-/metadata/new/10/20",
                "/assets/profile/-/metadata/link/new/10/",
            ],
        )
        fun `throws when GET query modifiers are invalid`(path: String) =
            runTest {
                shouldThrow<InvalidQuerySelectorsException> {
                    requestContextFactory.fromGetRequest(
                        path = path,
                        headers = HeadersBuilder().build(),
                        queryParameters = Parameters.Empty,
                    )
                }
            }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/assets/profile/-/entry/-1",
                "/assets/profile/-/entry/abc",
                "/assets/profile/-/metadata/entry/-1",
                "/assets/profile/-/metadata/entry/abc",
            ],
        )
        fun `entryId must be positive when fetching GET request context`(path: String) =
            runTest {
                shouldThrow<InvalidQuerySelectorsException> {
                    requestContextFactory.fromGetRequest(
                        path = path,
                        headers = HeadersBuilder().build(),
                        queryParameters = Parameters.Empty,
                    )
                }
            }

        @ParameterizedTest
        @EnumSource(value = ReturnFormat::class, mode = EnumSource.Mode.INCLUDE, names = ["REDIRECT", "CONTENT"])
        fun `cannot have limit greater than one with certain return formats`(returnFormat: ReturnFormat) =
            runTest {
                val exception =
                    shouldThrow<InvalidQuerySelectorsException> {
                        requestContextFactory.fromGetRequest(
                            path = "/assets/profile/user/123/-/$returnFormat",
                            headers = HeadersBuilder().build(),
                            queryParameters =
                                ParametersBuilder()
                                    .apply {
                                        append("limit", "3")
                                    }.build(),
                        )
                    }

                exception.message shouldBe "Invalid query modifiers: [${returnFormat.name}]"
            }

        @Test
        fun `path can only have one namespace separator in GET request context`() =
            runTest {
                val path = "/assets/profile/-/-/metadata/new/10/"
                val exception =
                    shouldThrow<InvalidPathException> {
                        requestContextFactory.fromGetRequest(
                            path = path,
                            headers = HeadersBuilder().build(),
                            queryParameters = Parameters.Empty,
                        )
                    }
                exception.message shouldBe "$path has more than one '-' segment"
            }

        @ParameterizedTest
        @MethodSource("io.konifer.service.context.RequestContextFactoryTest#transformationSource")
        fun `can parse requested image attributes in GET request context`(
            parameters: Parameters,
            transformation: Transformation,
        ) = runTest {
            val path = "/assets/profile/-/new/link"
            storePersistedAsset(
                height = 100,
                width = 100,
                format = ImageFormat.PNG,
                path = "/profile/",
            )
            val context =
                requestContextFactory.fromGetRequest(
                    path = path,
                    headers = HeadersBuilder().build(),
                    queryParameters = parameters,
                )
            context.transformation shouldBe transformation
            context.labels shouldBe emptyMap()
        }

        @Test
        fun `cannot create GET context if requesting metadata with image attributes`() =
            runTest {
                val parameters =
                    ParametersBuilder(3)
                        .apply {
                            append("w", "10")
                            append("h", "20")
                            append("format", "png")
                        }.build()

                val exception =
                    shouldThrow<InvalidPathException> {
                        requestContextFactory.fromGetRequest(
                            path = "/assets/profile/-/new/metadata/",
                            headers = HeadersBuilder().build(),
                            queryParameters = parameters,
                        )
                    }
                exception.message shouldBe "Cannot specify image attributes when requesting asset metadata"
            }

        @Test
        fun `can parse GET asset path from the uri request path`() =
            runTest {
                val context =
                    requestContextFactory.fromGetRequest(
                        path = "/assets/profile/123/-/metadata/",
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder()
                                .apply {
                                    append("limit", "10")
                                }.build(),
                    )

                context.path shouldBe "/profile/123/"
            }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/ASSETS/profile/-/new/10/",
                "/Assets/profile/-/new/10/",
                "/Asssetts/profile/-/new/10/",
                "/profile/-/new/10/",
            ],
        )
        fun `throws if GET uri path does not start with correct prefix`(path: String) =
            runTest {
                val exception =
                    shouldThrow<InvalidPathException> {
                        requestContextFactory.fromGetRequest(
                            path = path,
                            headers = HeadersBuilder().build(),
                            queryParameters = Parameters.Empty,
                        )
                    }

                exception.message shouldBe "Asset path must start with: /assets"
            }

        @Test
        fun `can parse labels in request`() =
            runTest {
                val path = "/assets/profile/-/new/link/"
                storePersistedAsset(
                    height = 100,
                    width = 100,
                    format = ImageFormat.PNG,
                    path = "/profile/",
                )
                val context =
                    requestContextFactory.fromGetRequest(
                        path = path,
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder(6)
                                .apply {
                                    append("h", "100")
                                    append("w", "500")
                                    append("format", "jpg")
                                    append("phone", "iphone")
                                    append("case", "soft")
                                    append("label:h", "hello")
                                }.build(),
                    )
                context.pathConfiguration shouldBe PathConfiguration.DEFAULT
                context.transformation?.height shouldBe 100
                context.transformation?.width shouldBe 500
                context.transformation?.format shouldBe ImageFormat.JPEG
                context.labels shouldContainExactly
                    mapOf(
                        "phone" to "iphone",
                        "case" to "soft",
                        "h" to "hello",
                    )
            }

        @Test
        fun `some label is used when duplicates exist in request`() =
            runTest {
                val path = "/assets/profile/-/new/link/"
                storePersistedAsset(
                    height = 100,
                    width = 100,
                    format = ImageFormat.PNG,
                    path = "/profile/",
                )
                val context =
                    requestContextFactory.fromGetRequest(
                        path = path,
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder(6)
                                .apply {
                                    append("h", "100")
                                    append("w", "500")
                                    append("format", "jpg")
                                    append("phone", "iphone")
                                    append("case", "soft")
                                    append("case", "hello")
                                }.build(),
                    )
                context.pathConfiguration shouldBe PathConfiguration.DEFAULT
                context.transformation?.height shouldBe 100
                context.transformation?.width shouldBe 500
                context.transformation?.format shouldBe ImageFormat.JPEG
                context.labels shouldContainKey "phone"
                context.labels shouldContainKey "case"
                context.labels["case"] shouldBeOneOf listOf("hello", "soft")
            }

        @Test
        fun `some label is used when duplicates exist and one is namespaced in request`() =
            runTest {
                val path = "/assets/profile/-/new/link/"
                storePersistedAsset(
                    height = 100,
                    width = 100,
                    format = ImageFormat.PNG,
                    path = "/profile/",
                )
                val context =
                    requestContextFactory.fromGetRequest(
                        path = path,
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder(6)
                                .apply {
                                    append("h", "100")
                                    append("w", "500")
                                    append("format", "jpg")
                                    append("phone", "iphone")
                                    append("case", "soft")
                                    append("label:case", "hello")
                                }.build(),
                    )
                context.pathConfiguration shouldBe PathConfiguration.DEFAULT
                context.transformation?.height shouldBe 100
                context.transformation?.width shouldBe 500
                context.transformation?.format shouldBe ImageFormat.JPEG
                context.labels shouldContainKey "phone"
                context.labels shouldContainKey "case"
                context.labels["case"] shouldBeOneOf listOf("hello", "soft")
            }

        @Test
        fun `format is derived from accept header if not supplied in profile or query param`() =
            runTest {
                storePersistedAsset(
                    height = 100,
                    width = 100,
                    format = ImageFormat.PNG,
                    path = "/profile/",
                )
                val context =
                    requestContextFactory.fromGetRequest(
                        path = "/assets/profile/-/content/",
                        headers =
                            HeadersBuilder()
                                .apply {
                                    append(HttpHeaders.Accept, "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
                                }.build(),
                        queryParameters = ParametersBuilder().build(),
                    )

                context.transformation?.format shouldBe ImageFormat.AVIF
            }

        @Test
        fun `format is derived from accept header if not supplied in profile or query param and priority is respected`() =
            runTest {
                storePersistedAsset(
                    height = 100,
                    width = 100,
                    format = ImageFormat.PNG,
                    path = "/profile/",
                )
                val context =
                    requestContextFactory.fromGetRequest(
                        path = "/assets/profile/-/content/",
                        headers =
                            HeadersBuilder()
                                .apply {
                                    append(HttpHeaders.Accept, "image/webp;q=0.8,image/gif;q=0.9,image/png;q=0.8,image/*;q=0.8,*/*;q=0.5")
                                }.build(),
                        queryParameters = ParametersBuilder().build(),
                    )

                context.transformation?.format shouldBe ImageFormat.GIF
            }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "*/*", "image/*",
            ],
        )
        fun `original variant format is set in context if accept header is generic`(accept: String) =
            runTest {
                val asset =
                    storePersistedAsset(
                        height = 100,
                        width = 100,
                        format = ImageFormat.PNG,
                        path = "/profile/",
                    )
                val context =
                    requestContextFactory.fromGetRequest(
                        path = "/assets/profile/-/content/",
                        headers =
                            HeadersBuilder()
                                .apply {
                                    append(HttpHeaders.Accept, accept)
                                }.build(),
                        queryParameters = ParametersBuilder().build(),
                    )

                context.transformation?.format shouldBe
                    asset.variants
                        .first { it.isOriginalVariant }
                        .transformation.format
            }

        @Test
        fun `variant profile format overwrites accept header`() =
            runTest {
                val profileName = "small"
                val variantConfig =
                    createRequestedImageTransformation(
                        width = 10,
                        height = 10,
                        format = ImageFormat.HEIC,
                    )
                every {
                    variantProfileRepository.fetch(profileName)
                } returns variantConfig
                val context =
                    requestContextFactory.fromGetRequest(
                        path = "/assets/profile/-/content/",
                        headers =
                            HeadersBuilder()
                                .apply {
                                    append(HttpHeaders.Accept, "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
                                }.build(),
                        queryParameters =
                            ParametersBuilder()
                                .apply {
                                    append("profile", profileName)
                                }.build(),
                    )

                context.transformation?.format shouldNotBe null
                context.transformation?.format shouldBe variantConfig.format
            }

        @Test
        fun `format specified in query parameter overwrites accept header`() =
            runTest {
                storePersistedAsset(
                    height = 100,
                    width = 100,
                    format = ImageFormat.PNG,
                    path = "/profile/",
                )
                val context =
                    requestContextFactory.fromGetRequest(
                        path = "/assets/profile/-/content/",
                        headers =
                            HeadersBuilder()
                                .apply {
                                    append(HttpHeaders.Accept, "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
                                }.build(),
                        queryParameters =
                            ParametersBuilder()
                                .apply {
                                    append("format", "heic")
                                }.build(),
                    )

                context.transformation?.format shouldNotBe null
                context.transformation?.format shouldBe ImageFormat.HEIC
            }
    }

    @Nested
    inner class DeleteRequestContextTests {
        @ParameterizedTest
        @MethodSource("io.konifer.service.context.RequestContextFactoryTest#deleteModifierSource")
        fun `can fetch DELETE request context with query modifiers`(
            path: String,
            parameters: Parameters,
            deleteModifiers: DeleteModifiers,
        ) {
            val context =
                requestContextFactory.fromDeleteRequest(
                    path = path,
                    queryParameters = parameters,
                )

            context.modifiers shouldBe deleteModifiers
        }

        @Test
        fun `can fetch DELETE request context with entryId`() =
            runTest {
                val context = requestContextFactory.fromDeleteRequest("/assets/profile/-/entry/10", Parameters.Empty)

                context.modifiers shouldBe
                    DeleteModifiers(
                        entryId = 10,
                    )
            }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/assets/profile/-/neww/",
                "/assets/profile/-/entry/-1",
                "/assets/profile/-/-10/",
                "/assets/profile/-/recursive/all",
                "/assets/profile/-/recursive/entry/1",
                "/assets/profile/-/recursive/new",
                "/assets/profile/-/recursive/1",
            ],
        )
        fun `throws when DELETE query modifiers are invalid`(path: String) {
            shouldThrow<InvalidDeleteSelectorsException> {
                requestContextFactory.fromDeleteRequest(path, Parameters.Empty)
            }
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/assets/profile/-/entry/-1",
                "/assets/profile/-/entry/abc",
            ],
        )
        fun `entryId must be positive when fetching DELETE request context`(path: String) {
            shouldThrow<InvalidDeleteSelectorsException> {
                requestContextFactory.fromDeleteRequest(path, Parameters.Empty)
            }
        }

        @Test
        fun `path can only have one namespace separator in DELETE request context`() {
            val path = "/assets/profile/-/-/1/"
            val exception =
                shouldThrow<InvalidPathException> {
                    requestContextFactory.fromDeleteRequest(path, Parameters.Empty)
                }
            exception.message shouldBe "$path has more than one '-' segment"
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/ASSETS/profile/-/1/",
                "/Assets/profile/-/1/",
                "/Asssetts/profile/-/1/",
                "/profile/-/1/",
            ],
        )
        fun `throws if DELETE uri path does not start with correct prefix`(path: String) {
            val exception =
                shouldThrow<InvalidPathException> {
                    requestContextFactory.fromDeleteRequest(path, Parameters.Empty)
                }

            exception.message shouldBe "Asset path must start with: /assets"
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/assets/profile/-/new/",
                "/assets/profile/-/recursive",
            ],
        )
        fun `labels are added to delete context if supplied`(path: String) {
            val context =
                requestContextFactory.fromDeleteRequest(
                    path,
                    ParametersBuilder(6)
                        .apply {
                            append("phone", "iphone")
                            append("case", "soft")
                            append("label:h", "hello")
                        }.build(),
                )
            context.labels shouldContainExactly
                mapOf(
                    "phone" to "iphone",
                    "case" to "soft",
                    "h" to "hello",
                )
        }
    }

    @Nested
    inner class StoreRequestContextTests {
        @Test
        fun `can create store asset request context`() {
            val path = "/assets/profile/123"
            val context = requestContextFactory.fromStoreRequest(path, "image/png")

            context.pathConfiguration shouldBe PathConfiguration.DEFAULT
            context.path shouldBe "/profile/123"
        }

        @Test
        fun `can create store asset request context if mimeType is permitted`() {
            val path = "/assets/profile/123"
            every {
                pathConfigurationRepository.fetch("/profile/123")
            } returns
                PathConfiguration(
                    allowedContentTypes = listOf("image/png"),
                    image = ImageProperties.DEFAULT,
                    eagerVariants = listOf(),
                    objectStore = ObjectStoreProperties.DEFAULT,
                    preProcessing = PreProcessingProperties.DEFAULT,
                    cacheControl = CacheControlProperties.DEFAULT,
                )
            val context = requestContextFactory.fromStoreRequest(path, "image/png")

            context.pathConfiguration.allowedContentTypes shouldBe listOf("image/png")
            context.path shouldBe "/profile/123"
        }

        @Test
        fun `store asset path cannot have modifiers`() {
            val path = "/assets/profile/123/-/new"
            val exception =
                shouldThrow<InvalidPathException> {
                    requestContextFactory.fromStoreRequest(path, "image/png")
                }
            exception.message shouldBe "Store request cannot have modifiers in path: $path"
        }

        @Test
        fun `throws if content type is not permitted`() {
            val path = "/assets/profile/123"
            every {
                pathConfigurationRepository.fetch("/profile/123")
            } returns
                PathConfiguration(
                    allowedContentTypes = listOf("image/jpeg"),
                    image = ImageProperties.DEFAULT,
                    eagerVariants = listOf(),
                    objectStore = ObjectStoreProperties.DEFAULT,
                    preProcessing = PreProcessingProperties.DEFAULT,
                    cacheControl = CacheControlProperties.DEFAULT,
                )

            val exception =
                shouldThrow<ContentTypeNotPermittedException> {
                    requestContextFactory.fromStoreRequest(path, "image/png")
                }
            exception.message shouldBe "Content type: image/png not permitted"
        }
    }

    @Nested
    inner class UpdateRequestContextTests {
        @Test
        fun `can create update request context`() {
            val path = "/assets/profile/123/-/entry/1"

            val context =
                shouldNotThrowAny {
                    requestContextFactory.fromUpdateRequest(path)
                }
            context.path shouldBe "/profile/123/"
            context.entryId shouldBe 1L
        }

        @Test
        fun `cannot specify return format`() {
            val path = "/assets/profile/123/-/entry/1/metadata"

            val exception =
                shouldThrow<InvalidPathException> {
                    requestContextFactory.fromUpdateRequest(path)
                }
            exception.message shouldBe "Return format cannot be supplied on update request"
        }

        @Test
        fun `cannot specify orderBy`() {
            val path = "/assets/profile/123/-/new/entry/1"

            val exception =
                shouldThrow<InvalidPathException> {
                    requestContextFactory.fromUpdateRequest(path)
                }
            exception.message shouldContain "Invalid query modifiers"
        }

        @Test
        fun `cannot specify limit`() {
            val path = "/assets/profile/123/-/entry/1/3"

            val exception =
                shouldThrow<InvalidPathException> {
                    requestContextFactory.fromUpdateRequest(path)
                }
            exception.message shouldContain "Invalid query modifiers"
        }

        @Test
        fun `update request context requires an entryId`() {
            val path = "/assets/profile/123"

            val exception =
                shouldThrow<InvalidPathException> {
                    requestContextFactory.fromUpdateRequest(path)
                }
            exception.message shouldBe "Entry id must be specified on an update request"
        }
    }
}
