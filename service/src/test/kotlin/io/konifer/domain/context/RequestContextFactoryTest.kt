package io.konifer.domain.context

import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.TransformableColorSpace
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.common.selector.Order
import io.konifer.common.selector.ReturnFormat
import io.konifer.createRequestedImageTransformation
import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.context.selector.DeleteSelection
import io.konifer.domain.context.selector.QuerySelectors
import io.konifer.domain.context.selector.SpecifiedInRequest
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.OnDemandVariantMode
import io.konifer.domain.variant.OnDemandVariantProperties
import io.konifer.domain.variant.TransformProperties
import io.konifer.infrastructure.path.TriePathConfigurationRepository
import io.konifer.infrastructure.variant.profile.ConfigurationVariantProfileRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.RequestConnectionPoint
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
import org.junitpioneer.jupiter.cartesian.CartesianTest

class RequestContextFactoryTest {
    companion object {
        @JvmStatic
        fun queryModifierSource(): List<Arguments> =
            listOf(
                arguments(
                    "/assets/profile/-/entries",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.ENTRIES,
                        order = Order.NEW,
                        limit = (20).toLimit(),
                        specifiedModifiers = SpecifiedInRequest(returnFormat = true),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/entries",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.ENTRIES,
                        order = Order.NEW,
                        limit = (10).toLimit(),
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/modified/entries",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.ENTRIES,
                        order = Order.MODIFIED,
                        limit = (10).toLimit(),
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
                        limit = (1).toLimit(),
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
                        limit = (1).toLimit(),
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/eNtRiEs/",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.ENTRIES,
                        order = Order.NEW,
                        limit = (10).toLimit(),
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/entries",
                    ParametersBuilder()
                        .apply {
                            append("limit", "-1")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.ENTRIES,
                        order = Order.NEW,
                        limit = (-1).toLimit(),
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/info",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.INFO,
                        order = Order.NEW,
                        limit = (1).toLimit(),
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/entries",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.ENTRIES,
                        order = Order.NEW,
                        limit = (10).toLimit(),
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/entries",
                    ParametersBuilder()
                        .apply {
                            append("limit", "-1")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.ENTRIES,
                        order = Order.NEW,
                        limit = (-1).toLimit(),
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/info",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.INFO,
                        order = Order.NEW,
                        limit = (1).toLimit(),
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
                        limit = (1).toLimit(),
                        specifiedModifiers =
                            SpecifiedInRequest(
                                orderBy = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile-/",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        order = Order.NEW,
                        limit = (1).toLimit(),
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile-",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        order = Order.NEW,
                        limit = (1).toLimit(),
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        order = Order.NEW,
                        limit = (1).toLimit(),
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile/",
                    Parameters.Empty,
                    QuerySelectors(
                        returnFormat = ReturnFormat.LINK,
                        order = Order.NEW,
                        limit = (1).toLimit(),
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile/-/new/entries",
                    ParametersBuilder()
                        .apply {
                            append("limit", "10")
                        }.build(),
                    QuerySelectors(
                        returnFormat = ReturnFormat.ENTRIES,
                        order = Order.NEW,
                        limit = (10).toLimit(),
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
                arguments("/assets/profile", Parameters.build { append("limit", "-1") }, DeleteSelection.AllAtPath),
                arguments("/assets/profile/", Parameters.build { append("limit", "-1") }, DeleteSelection.AllAtPath),
                arguments("/assets/profile/-/recursive", Parameters.Empty, DeleteSelection.Recursive),
                arguments("/assets/profile/-/entry/10", Parameters.Empty, DeleteSelection.Entry(10)),
            )

        @JvmStatic
        fun getEntryIdSource(): List<Arguments> =
            listOf(
                arguments(
                    "/assets/profile/-/entry/10/info",
                    QuerySelectors(
                        returnFormat = ReturnFormat.INFO,
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
                            append("width", "10")
                            append("height", "20")
                            append("format", "png")
                        }.build(),
                    RequestedTransformation(
                        width = 10.toDimension(),
                        height = 20.toDimension(),
                        format = ImageFormat.PNG,
                        fit = Fit.FIT,
                        colorSpace = TransformableColorSpace.ORIGIN,
                    ),
                ),
                arguments(
                    ParametersBuilder(2)
                        .apply {
                            append("width", "10")
                            append("height", "20")
                        }.build(),
                    RequestedTransformation(
                        width = 10.toDimension(),
                        height = 20.toDimension(),
                        fit = Fit.FIT,
                        colorSpace = TransformableColorSpace.ORIGIN,
                    ),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("width", "10")
                        }.build(),
                    RequestedTransformation(
                        width = 10.toDimension(),
                        fit = Fit.FIT,
                        colorSpace = TransformableColorSpace.ORIGIN,
                    ),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("format", "jpg")
                        }.build(),
                    RequestedTransformation(
                        format = ImageFormat.JPEG,
                        fit = Fit.FIT,
                        colorSpace = TransformableColorSpace.ORIGIN,
                    ),
                ),
            )

        @JvmStatic
        fun restrictedTransformationSource(): List<Arguments> =
            listOf(
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("height", "100")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("width", "100")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("fit", "fit")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("gravity", "center")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("rotate", "90")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("flip", "v")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("filter", "sepia")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("blur", "100")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("quality", "100")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("pad", "100")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("strip", "exif")
                        }.build(),
                ),
                arguments(
                    ParametersBuilder(1)
                        .apply {
                            append("color-space", "srgb")
                        }.build(),
                ),
            )
    }

    private val pathConfigurationRepository = mockk<TriePathConfigurationRepository>()
    private val variantProfileRepository = mockk<ConfigurationVariantProfileRepository>()
    private val requestContextValidator =
        RequestContextValidator(
            variantProfileRepository,
            io.konifer.domain.transformation
                .TransformationNormalizer(),
        )
    private val requestContextFactory =
        RequestContextFactory(pathConfigurationRepository, variantProfileRepository, requestContextValidator)
    private val origin = mockk<RequestConnectionPoint>(relaxed = true)

    private fun RequestContextFactory.fromFetchRequest(
        path: String,
        headers: Headers,
        queryParameters: Parameters,
    ): QueryRequestContext =
        fromFetchRequest(
            path = path,
            headers = headers,
            queryParameters = queryParameters,
            origin = origin,
        )

    @BeforeEach
    fun beforeEach() {
        every {
            pathConfigurationRepository.fetch(any())
        } returns PathConfiguration.default
    }

    @Nested
    inner class PathBoundaryTests {
        @ParameterizedTest
        @ValueSource(
            strings = [
                "album-/photo", "album-", "-album/photo", "--/photo", "album--/photo-",
                "entry/info/new", "Album-/Photo",
            ],
        )
        fun `ordinary hyphens and selector names remain part of the asset path`(assetPath: String) {
            val path = "/assets/$assetPath"
            requestContextFactory.fromStoreRequest(path).path shouldBe "/$assetPath"
            requestContextFactory.fromFetchRequest(path, Headers.Empty, Parameters.Empty).path shouldBe "/$assetPath"
            val delete = requestContextFactory.fromDeleteRequest(path, Parameters.build { append("limit", "-1") })
            delete.path shouldBe "/$assetPath"
            delete.selection shouldBe DeleteSelection.AllAtPath

            val entryPath = "$path/-/entry/42"
            val fetch = requestContextFactory.fromFetchRequest(entryPath, Headers.Empty, Parameters.Empty)
            fetch.path shouldBe "/$assetPath/"
            fetch.selectors.entryId shouldBe 42L
            val update = requestContextFactory.fromUpdateRequest(entryPath)
            update.path shouldBe "/$assetPath/"
            update.entryId shouldBe 42L
            val entryDelete = requestContextFactory.fromDeleteRequest(entryPath, Parameters.Empty)
            entryDelete.path shouldBe "/$assetPath/"
            entryDelete.selection shouldBe DeleteSelection.Entry(42)
            shouldThrow<InvalidPathException> { requestContextFactory.fromStoreRequest(entryPath) }
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/assets/album/-", "/assets/album/-/", "/assets/-", "/assets/-/",
                "/assets/album/-/info/-/entry/42", "/assets/album/-/info/-",
                "/assets/album/-/info/-/", "/assets/-/-/info",
                "/assets-other/album", "/assetsalbum/album", "/ASSETS/album",
            ],
        )
        fun `all methods reject malformed boundaries and prefixes`(path: String) {
            shouldThrow<InvalidPathException> { requestContextFactory.fromStoreRequest(path) }
            shouldThrow<InvalidPathException> {
                requestContextFactory.fromFetchRequest(path, Headers.Empty, Parameters.Empty)
            }
            shouldThrow<InvalidPathException> { requestContextFactory.fromUpdateRequest(path) }
            shouldThrow<InvalidPathException> {
                requestContextFactory.fromDeleteRequest(path, Parameters.build { append("limit", "-1") })
            }
        }

        @Test
        fun `root selectors and existing trailing slash handling remain supported`() {
            requestContextFactory.fromStoreRequest("/assets").path shouldBe ""
            requestContextFactory.fromStoreRequest("/assets/album-/").path shouldBe "/album-/"
            requestContextFactory.fromFetchRequest("/assets/album-/", Headers.Empty, Parameters.Empty).path shouldBe "/album-"
            val root = requestContextFactory.fromFetchRequest("/assets/-/info/", Headers.Empty, Parameters.Empty)
            root.path shouldBe "/"
            root.selectors.returnFormat shouldBe ReturnFormat.INFO
        }
    }

    @Nested
    inner class FetchRequestContextTests {
        @ParameterizedTest
        @ValueSource(strings = ["planar", "PLANAR", "pLaNaR", "interleaved", "INTERLEAVED"])
        fun `parses pixel layouts without treating them as labels`(layout: String) =
            runTest {
                val context =
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/pixels/",
                        headers = Headers.Empty,
                        queryParameters =
                            Parameters.build {
                                append("format", "pixels")
                                append("pixel-layout", layout)
                            },
                    )

                context.requestedTransformation!!.pixelLayout shouldBe
                    PixelLayout.valueOf(layout.uppercase())
                context.labels shouldBe AssetLabels.empty
            }

        @Test
        fun `uses profile pixel layout unless overridden by the request`() =
            runTest {
                every { variantProfileRepository.fetch("pixels") } returns
                    RequestedTransformation(format = ImageFormat.PIXELS, pixelLayout = PixelLayout.PLANAR)

                listOf(null, "interleaved").forEach { override ->
                    val context =
                        requestContextFactory.fromFetchRequest(
                            path = "/assets/pixels/",
                            headers = Headers.Empty,
                            queryParameters =
                                Parameters.build {
                                    append("profile", "pixels")
                                    override?.let { append("pixel-layout", it) }
                                },
                        )

                    context.requestedTransformation!!.pixelLayout shouldBe
                        if (override == null) PixelLayout.PLANAR else PixelLayout.INTERLEAVED
                }
            }

        @Test
        fun `rejects unsupported pixel layouts`() =
            runTest {
                shouldThrow<IllegalArgumentException> {
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/pixels/",
                        headers = Headers.Empty,
                        queryParameters =
                            Parameters.build {
                                append("format", "pixels")
                                append("pixel-layout", "tiled")
                            },
                    )
                }
            }

        @ParameterizedTest
        @MethodSource("io.konifer.domain.context.RequestContextFactoryTest#queryModifierSource")
        fun `can fetch GET request context with query modifiers`(
            path: String,
            queryParameters: Parameters,
            expectedQuerySelectors: QuerySelectors,
        ) = runTest {
            val context =
                requestContextFactory.fromFetchRequest(
                    path = path,
                    headers = HeadersBuilder().build(),
                    queryParameters = queryParameters,
                )

            context.pathConfiguration shouldBe PathConfiguration.default
            context.selectors shouldBe expectedQuerySelectors
            context.labels shouldBe AssetLabels.empty
            context.request.origin shouldBe origin
        }

        @ParameterizedTest
        @MethodSource("io.konifer.domain.context.RequestContextFactoryTest#getEntryIdSource")
        fun `can fetch GET request context with entryId`(
            path: String,
            expectedQuerySelectors: QuerySelectors,
        ) = runTest {
            val context =
                requestContextFactory.fromFetchRequest(
                    path = path,
                    headers = HeadersBuilder().build(),
                    queryParameters = Parameters.Empty,
                )

            context.pathConfiguration shouldBe PathConfiguration.default
            context.selectors shouldBe expectedQuerySelectors
            context.labels shouldBe AssetLabels.empty
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
                        colorSpace = TransformableColorSpace.P3,
                    )
                every {
                    variantProfileRepository.fetch(profileName)
                } returns variantConfig

                val context =
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/user/",
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder(1)
                                .apply {
                                    append("profile", profileName)
                                }.build(),
                    )

                context.pathConfiguration shouldBe PathConfiguration.default
                context.requestedTransformation shouldBe variantConfig
                context.labels shouldBe AssetLabels.empty
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
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/user/",
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder(4)
                                .apply {
                                    append("profile", profileName)
                                    append("height", "100")
                                    append("width", "500")
                                    append("format", "jpg")
                                }.build(),
                    )

                context.pathConfiguration shouldBe PathConfiguration.default
                context.requestedTransformation!!.height shouldBe 100.toDimension()
                context.requestedTransformation.width shouldBe 500.toDimension()
                context.requestedTransformation.format shouldBe ImageFormat.JPEG
                context.labels shouldBe AssetLabels.empty
            }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/assets/profile/-/new/info/10/",
                "/assets/profile/-/infoo/new/10/",
                "/assets/profile/-/info/new/-1/",
                "/assets/profile/-/info/neww/10/",
                "/assets/profile/-/info/new/0/",
                "/assets/profile/-/10/info/new/",
                "/assets/profile/-/info/info/info/",
                "/assets/profile/-/new/new/new/",
                "/assets/profile/-/10/10/10/",
                "/assets/profile/-/info/new/10/20",
                "/assets/profile/-/info/link/new/10/",
            ],
        )
        fun `throws when GET query modifiers are invalid`(path: String) =
            runTest {
                shouldThrow<InvalidQuerySelectorsException> {
                    requestContextFactory.fromFetchRequest(
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
                "/assets/profile/-/info/entry/-1",
                "/assets/profile/-/info/entry/abc",
            ],
        )
        fun `entryId must be positive when fetching GET request context`(path: String) =
            runTest {
                shouldThrow<InvalidQuerySelectorsException> {
                    requestContextFactory.fromFetchRequest(
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
                        requestContextFactory.fromFetchRequest(
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
                val path = "/assets/profile/-/-/info/new/10/"
                val exception =
                    shouldThrow<InvalidPathException> {
                        requestContextFactory.fromFetchRequest(
                            path = path,
                            headers = HeadersBuilder().build(),
                            queryParameters = Parameters.Empty,
                        )
                    }
                exception.message shouldBe "$path has more than one '-' segment"
            }

        @ParameterizedTest
        @MethodSource("io.konifer.domain.context.RequestContextFactoryTest#transformationSource")
        fun `can parse requested image attributes in GET request context`(
            parameters: Parameters,
            transformation: RequestedTransformation,
        ) = runTest {
            val path = "/assets/profile/-/new/link"
            val context =
                requestContextFactory.fromFetchRequest(
                    path = path,
                    headers = HeadersBuilder().build(),
                    queryParameters = parameters,
                )
            context.requestedTransformation shouldBe transformation
            context.labels shouldBe AssetLabels.empty
        }

        @Test
        fun `cannot create GET context if requesting metadata with image attributes`() =
            runTest {
                val parameters =
                    ParametersBuilder(3)
                        .apply {
                            append("width", "10")
                            append("height", "20")
                            append("format", "png")
                        }.build()

                val exception =
                    shouldThrow<InvalidPathException> {
                        requestContextFactory.fromFetchRequest(
                            path = "/assets/profile/-/new/info/",
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
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/profile/123/-/entries/",
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
                        requestContextFactory.fromFetchRequest(
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
                val context =
                    requestContextFactory.fromFetchRequest(
                        path = path,
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder(6)
                                .apply {
                                    append("height", "100")
                                    append("width", "500")
                                    append("format", "jpg")
                                    append("phone", "iphone")
                                    append("case", "soft")
                                    append("label:height", "hello")
                                }.build(),
                    )
                context.pathConfiguration shouldBe PathConfiguration.default
                context.requestedTransformation!!.height shouldBe 100.toDimension()
                context.requestedTransformation.width shouldBe 500.toDimension()
                context.requestedTransformation.format shouldBe ImageFormat.JPEG
                context.labels.asMap() shouldContainExactly
                    mapOf(
                        "phone" to "iphone",
                        "case" to "soft",
                        "height" to "hello",
                    )
            }

        @Test
        fun `some label is used when duplicates exist in request`() =
            runTest {
                val path = "/assets/profile/-/new/link/"
                val context =
                    requestContextFactory.fromFetchRequest(
                        path = path,
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder(6)
                                .apply {
                                    append("height", "100")
                                    append("width", "500")
                                    append("format", "jpg")
                                    append("phone", "iphone")
                                    append("case", "soft")
                                    append("case", "hello")
                                }.build(),
                    )
                context.pathConfiguration shouldBe PathConfiguration.default
                context.requestedTransformation!!.height shouldBe 100.toDimension()
                context.requestedTransformation.width shouldBe 500.toDimension()
                context.requestedTransformation.format shouldBe ImageFormat.JPEG
                context.labels.asMap() shouldContainKey "phone"
                context.labels.asMap() shouldContainKey "case"
                context.labels.asMap()["case"] shouldBeOneOf listOf("hello", "soft")
            }

        @Test
        fun `some label is used when duplicates exist and one is namespaced in request`() =
            runTest {
                val path = "/assets/profile/-/new/link/"
                val context =
                    requestContextFactory.fromFetchRequest(
                        path = path,
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder(6)
                                .apply {
                                    append("height", "100")
                                    append("width", "500")
                                    append("format", "jpg")
                                    append("phone", "iphone")
                                    append("case", "soft")
                                    append("label:case", "hello")
                                }.build(),
                    )
                context.pathConfiguration shouldBe PathConfiguration.default
                context.requestedTransformation!!.height shouldBe 100.toDimension()
                context.requestedTransformation.width shouldBe 500.toDimension()
                context.requestedTransformation.format shouldBe ImageFormat.JPEG
                context.labels.asMap() shouldContainKey "phone"
                context.labels.asMap() shouldContainKey "case"
                context.labels.asMap()["case"] shouldBeOneOf listOf("hello", "soft")
            }

        @Test
        fun `format is derived from accept header if not supplied in profile or query param`() =
            runTest {
                val context =
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/profile/-/content/",
                        headers =
                            HeadersBuilder()
                                .apply {
                                    append(HttpHeaders.Accept, "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
                                }.build(),
                        queryParameters = ParametersBuilder().build(),
                    )

                context.requestedTransformation!!.format shouldBe ImageFormat.AVIF
            }

        @Test
        fun `format is derived from accept header if not supplied in profile or query param and priority is respected`() =
            runTest {
                val context =
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/profile/-/content/",
                        headers =
                            HeadersBuilder()
                                .apply {
                                    append(HttpHeaders.Accept, "image/webp;q=0.8,image/gif;q=0.9,image/png;q=0.8,image/*;q=0.8,*/*;q=0.5")
                                }.build(),
                        queryParameters = ParametersBuilder().build(),
                    )

                context.requestedTransformation!!.format shouldBe ImageFormat.GIF
            }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "*/*", "image/*",
            ],
        )
        fun `original variant is selected if accept header is generic`(accept: String) =
            runTest {
                val context =
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/profile/-/content/",
                        headers =
                            HeadersBuilder()
                                .apply {
                                    append(HttpHeaders.Accept, accept)
                                }.build(),
                        queryParameters = ParametersBuilder().build(),
                    )

                context.requestedTransformation shouldBe null
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
                    requestContextFactory.fromFetchRequest(
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

                context.requestedTransformation!!.format shouldNotBe null
                context.requestedTransformation.format shouldBe variantConfig.format
            }

        @Test
        fun `format specified in query parameter overwrites accept header`() =
            runTest {
                val context =
                    requestContextFactory.fromFetchRequest(
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

                context.requestedTransformation!!.format shouldNotBe null
                context.requestedTransformation.format shouldBe ImageFormat.HEIC
            }

        @ParameterizedTest
        @MethodSource("io.konifer.domain.context.RequestContextFactoryTest#restrictedTransformationSource")
        fun `profile-only policy validation is deferred until original attributes are available`(parameters: Parameters) =
            runTest {
                every {
                    pathConfigurationRepository.fetch(path = any())
                } returns
                    PathConfiguration(
                        transform =
                            TransformProperties(
                                onDemandVariant =
                                    OnDemandVariantProperties(
                                        mode = OnDemandVariantMode.PROFILE_ONLY,
                                    ),
                            ),
                    )
                shouldNotThrowAny {
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/profile/-/content/",
                        headers = HeadersBuilder().build(),
                        queryParameters = parameters,
                    )
                }
            }

        @ParameterizedTest
        @EnumSource(ReturnFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["INFO", "ENTRIES"])
        fun `profile-only parsing supports transformations for all variant return formats`(returnFormat: ReturnFormat) =
            runTest {
                every {
                    pathConfigurationRepository.fetch(path = any())
                } returns
                    PathConfiguration(
                        transform =
                            TransformProperties(
                                onDemandVariant =
                                    OnDemandVariantProperties(
                                        mode = OnDemandVariantMode.PROFILE_ONLY,
                                    ),
                            ),
                    )
                shouldNotThrowAny {
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/profile/-/${returnFormat.name.lowercase()}/",
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder()
                                .apply {
                                    append("width", "100")
                                }.build(),
                    )
                }
            }

        @ParameterizedTest
        @EnumSource(ReturnFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["INFO", "ENTRIES"])
        fun `can specify profile with profile_only mode`(returnFormat: ReturnFormat) =
            runTest {
                every {
                    pathConfigurationRepository.fetch(path = any())
                } returns
                    PathConfiguration(
                        transform =
                            TransformProperties(
                                onDemandVariant =
                                    OnDemandVariantProperties(
                                        mode = OnDemandVariantMode.PROFILE_ONLY,
                                    ),
                            ),
                    )
                every {
                    variantProfileRepository.fetch(profileName = "thumbnail")
                } returns
                    RequestedTransformation(
                        width = 100.toDimension(),
                        height = 100.toDimension(),
                        format = ImageFormat.JPEG,
                    )
                shouldNotThrowAny {
                    requestContextFactory.fromFetchRequest(
                        path = "/assets/profile/-/${returnFormat.name.lowercase()}/",
                        headers = HeadersBuilder().build(),
                        queryParameters =
                            ParametersBuilder()
                                .apply {
                                    append("profile", "thumbnail")
                                }.build(),
                    )
                }
            }

        @CartesianTest
        fun `can specify original variant with on-demand variant profile modes`(
            @CartesianTest.Enum(ReturnFormat::class) returnFormat: ReturnFormat,
            @CartesianTest.Enum(OnDemandVariantMode::class) mode: OnDemandVariantMode,
        ) = runTest {
            every {
                pathConfigurationRepository.fetch(path = any())
            } returns
                PathConfiguration(
                    transform =
                        TransformProperties(
                            onDemandVariant =
                                OnDemandVariantProperties(
                                    mode = mode,
                                ),
                        ),
                )
            shouldNotThrowAny {
                requestContextFactory.fromFetchRequest(
                    path = "/assets/profile/-/${returnFormat.name.lowercase()}/",
                    headers = HeadersBuilder().build(),
                    queryParameters = ParametersBuilder().build(),
                )
            }
        }
    }

    @Nested
    inner class DeleteRequestContextTests {
        @ParameterizedTest
        @MethodSource("io.konifer.domain.context.RequestContextFactoryTest#deleteModifierSource")
        fun `can fetch DELETE request context with query modifiers`(
            path: String,
            parameters: Parameters,
            deleteSelection: DeleteSelection,
        ) {
            val context =
                requestContextFactory.fromDeleteRequest(
                    path = path,
                    queryParameters = parameters,
                )

            context.selection shouldBe deleteSelection
        }

        @Test
        fun `can fetch DELETE request context with entryId`() =
            runTest {
                val context = requestContextFactory.fromDeleteRequest("/assets/profile/-/entry/10", Parameters.Empty)

                context.selection shouldBe DeleteSelection.Entry(10)
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
                "/assets/profile/-/entry/10",
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
                            append("label:height", "hello")
                        }.build(),
                )
            context.labels.asMap() shouldContainExactly
                mapOf(
                    "phone" to "iphone",
                    "case" to "soft",
                    "height" to "hello",
                )
        }
    }

    @Nested
    inner class StoreRequestContextTests {
        @Test
        fun `can create store asset request context`() {
            val path = "/assets/profile/123"
            val context = requestContextFactory.fromStoreRequest(path)

            context.pathConfiguration shouldBe PathConfiguration.default
            context.path shouldBe "/profile/123"
        }

        @Test
        fun `store asset path cannot have modifiers`() {
            val path = "/assets/profile/123/-/new"
            val exception =
                shouldThrow<InvalidPathException> {
                    requestContextFactory.fromStoreRequest(path)
                }
            exception.message shouldBe "Store request cannot have modifiers in path: $path"
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
            val path = "/assets/profile/123/-/entry/1/info"

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
