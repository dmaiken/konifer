package io.direkt.service.context

import io.createRequestedImageTransformation
import io.direkt.BaseUnitTest
import io.direkt.domain.image.Fit
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.ImageProperties
import io.direkt.domain.path.PathConfiguration
import io.direkt.domain.variant.Transformation
import io.direkt.infrastructure.objectstore.s3.S3PathProperties
import io.direkt.infrastructure.path.TriePathConfigurationRepository
import io.direkt.infrastructure.variant.profile.ConfigurationVariantProfileRepository
import io.direkt.service.transformation.TransformationNormalizer
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
                    "/assets/profile/-/metadata/created/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
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
                    "/assets/profile/-/metadata/modified/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.MODIFIED,
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
                    "/assets/profile/-/link/created/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
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
                    "/assets/profile/-/redirect/created/1",
                    QueryModifiers(
                        returnFormat = ReturnFormat.REDIRECT,
                        orderBy = OrderBy.CREATED,
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
                    "/assets/profile/-/content/created/1",
                    QueryModifiers(
                        returnFormat = ReturnFormat.CONTENT,
                        orderBy = OrderBy.CREATED,
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
                    "/assets/profile/-/mEtAData/created/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
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
                    "/assets/profile/-/metadata/created/all",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
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
                    "/assets/profile/-/created/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                orderBy = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata/created",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                orderBy = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
                        limit = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata/all",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
                        limit = -1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/all",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = -1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                limit = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/created",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                orderBy = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile/-",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile/",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                        specifiedModifiers = SpecifiedInRequest(),
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata/created/10/",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
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
                    "/assets/profile/-/created/10",
                    DeleteModifiers(
                        orderBy = OrderBy.CREATED,
                        limit = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/modified/10",
                    DeleteModifiers(
                        orderBy = OrderBy.MODIFIED,
                        limit = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/created/all",
                    DeleteModifiers(
                        orderBy = OrderBy.CREATED,
                        limit = -1,
                    ),
                ),
                arguments(
                    "/assets/profile/-/created/",
                    DeleteModifiers(
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/-/modified/",
                    DeleteModifiers(
                        orderBy = OrderBy.MODIFIED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/",
                    DeleteModifiers(
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                        recursive = false,
                    ),
                ),
                arguments(
                    "/assets/profile/-/",
                    DeleteModifiers(
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                        recursive = false,
                    ),
                ),
                arguments(
                    "/assets/profile/-/recursive",
                    DeleteModifiers(
                        recursive = true,
                    ),
                ),
                arguments(
                    "/assets/profile/-/all",
                    DeleteModifiers(
                        limit = -1,
                    ),
                ),
            )

        @JvmStatic
        fun getEntryIdSource(): List<Arguments> =
            listOf(
                arguments(
                    "/assets/profile/-/metadata/entry/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        entryId = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/content/entry/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.CONTENT,
                        entryId = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/redirect/entry/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.REDIRECT,
                        entryId = 10,
                        specifiedModifiers =
                            SpecifiedInRequest(
                                returnFormat = true,
                            ),
                    ),
                ),
                arguments(
                    "/assets/profile/-/link/entry/10",
                    QueryModifiers(
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
                    QueryModifiers(
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
                            append("mimeType", "image/png")
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
                            append("mimeType", "image/jpeg")
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
        @MethodSource("io.direkt.service.context.RequestContextFactoryTest#queryModifierSource")
        fun `can fetch GET request context with query modifiers`(
            path: String,
            expectedQueryModifiers: QueryModifiers,
        ) = runTest {
            val context = requestContextFactory.fromGetRequest(path, Parameters.Empty)

            context.pathConfiguration shouldBe PathConfiguration.DEFAULT
            context.modifiers shouldBe expectedQueryModifiers
            context.labels shouldBe emptyMap()
        }

        @ParameterizedTest
        @MethodSource("io.direkt.service.context.RequestContextFactoryTest#getEntryIdSource")
        fun `can fetch GET request context with entryId`(
            path: String,
            expectedQueryModifiers: QueryModifiers,
        ) = runTest {
            val context = requestContextFactory.fromGetRequest(path, Parameters.Empty)

            context.pathConfiguration shouldBe PathConfiguration.DEFAULT
            context.modifiers shouldBe expectedQueryModifiers
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
                        "/assets/user/",
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
                        "/assets/user/",
                        ParametersBuilder(4)
                            .apply {
                                append("profile", profileName)
                                append("h", "100")
                                append("w", "500")
                                append("mimeType", "image/jpeg")
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
                "/assets/profile/-/created/metadata/10/",
                "/assets/profile/-/metadataa/created/10/",
                "/assets/profile/-/metadata/created/-1/",
                "/assets/profile/-/metadata/ccreated/10/",
                "/assets/profile/-/metadata/created/0/",
                "/assets/profile/-/10/metadata/created/",
                "/assets/profile/-/metadata/metadata/metadata/",
                "/assets/profile/-/created/created/created/",
                "/assets/profile/-/10/10/10/",
                "/assets/profile/-/metadata/created/10/20",
                "/assets/profile/-/metadata/link/created/10/",
            ],
        )
        fun `throws when GET query modifiers are invalid`(path: String) =
            runTest {
                shouldThrow<InvalidQueryModifiersException> {
                    requestContextFactory.fromGetRequest(path, Parameters.Empty)
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
                shouldThrow<InvalidQueryModifiersException> {
                    requestContextFactory.fromGetRequest(path, Parameters.Empty)
                }
            }

        @ParameterizedTest
        @EnumSource(value = ReturnFormat::class, mode = EnumSource.Mode.INCLUDE, names = ["REDIRECT", "CONTENT"])
        fun `cannot have limit greater than one with certain return formats`(returnFormat: ReturnFormat) =
            runTest {
                val exception =
                    shouldThrow<InvalidQueryModifiersException> {
                        requestContextFactory.fromGetRequest("/assets/profile/user/123/-/$returnFormat/2", Parameters.Empty)
                    }

                exception.message shouldBe "Cannot have limit > 1 with return format of: ${returnFormat.name.lowercase()}"
            }

        @Test
        fun `path can only have one namespace separator in GET request context`() =
            runTest {
                val path = "/assets/profile/-/-/metadata/created/10/"
                val exception =
                    shouldThrow<InvalidPathException> {
                        requestContextFactory.fromGetRequest(path, Parameters.Empty)
                    }
                exception.message shouldBe "$path has more than one '-' segment"
            }

        @ParameterizedTest
        @MethodSource("io.direkt.service.context.RequestContextFactoryTest#transformationSource")
        fun `can parse requested image attributes in GET request context`(
            parameters: Parameters,
            transformation: Transformation,
        ) = runTest {
            val path = "/assets/profile/-/link/created/10/"
            storePersistedAsset(
                height = 100,
                width = 100,
                format = ImageFormat.PNG,
                path = "/profile/",
            )
            val context = requestContextFactory.fromGetRequest(path, parameters)
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
                            append("mimeType", "image/png")
                        }.build()

                val exception =
                    shouldThrow<InvalidPathException> {
                        requestContextFactory.fromGetRequest("/assets/profile/-/metadata/created/10/", parameters)
                    }
                exception.message shouldBe "Cannot specify image attributes when requesting asset metadata"
            }

        @Test
        fun `can parse GET asset path from the uri request path`() =
            runTest {
                val context = requestContextFactory.fromGetRequest("/assets/profile/123/-/metadata/2", Parameters.Empty)

                context.path shouldBe "/profile/123/"
            }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/ASSETS/profile/-/created/10/",
                "/Assets/profile/-/created/10/",
                "/Asssetts/profile/-/created/10/",
                "/profile/-/created/10/",
            ],
        )
        fun `throws if GET uri path does not start with correct prefix`(path: String) =
            runTest {
                val exception =
                    shouldThrow<InvalidPathException> {
                        requestContextFactory.fromGetRequest(path, Parameters.Empty)
                    }

                exception.message shouldBe "Asset path must start with: /assets"
            }

        @Test
        fun `can parse labels in request`() =
            runTest {
                val path = "/assets/profile/-/link/created/10/"
                storePersistedAsset(
                    height = 100,
                    width = 100,
                    format = ImageFormat.PNG,
                    path = "/profile/",
                )
                val context =
                    requestContextFactory.fromGetRequest(
                        path,
                        ParametersBuilder(6)
                            .apply {
                                append("h", "100")
                                append("w", "500")
                                append("mimeType", "image/jpeg")
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
                val path = "/assets/profile/-/link/created/10/"
                storePersistedAsset(
                    height = 100,
                    width = 100,
                    format = ImageFormat.PNG,
                    path = "/profile/",
                )
                val context =
                    requestContextFactory.fromGetRequest(
                        path,
                        ParametersBuilder(6)
                            .apply {
                                append("h", "100")
                                append("w", "500")
                                append("mimeType", "image/jpeg")
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
                val path = "/assets/profile/-/link/created/10/"
                storePersistedAsset(
                    height = 100,
                    width = 100,
                    format = ImageFormat.PNG,
                    path = "/profile/",
                )
                val context =
                    requestContextFactory.fromGetRequest(
                        path,
                        ParametersBuilder(6)
                            .apply {
                                append("h", "100")
                                append("w", "500")
                                append("mimeType", "image/jpeg")
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
    }

    @Nested
    inner class DeleteRequestContextTests {
        @ParameterizedTest
        @MethodSource("io.direkt.service.context.RequestContextFactoryTest#deleteModifierSource")
        fun `can fetch DELETE request context with query modifiers`(
            path: String,
            deleteModifiers: DeleteModifiers,
        ) {
            val context = requestContextFactory.fromDeleteRequest(path, Parameters.Empty)

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
                "/assets/profile/-/createdd/",
                "/assets/profile/-/entry/-1",
                "/assets/profile/-/-10/",
                "/assets/profile/-/recursive/all",
                "/assets/profile/-/recursive/entry/1",
                "/assets/profile/-/recursive/created",
                "/assets/profile/-/recursive/1",
            ],
        )
        fun `throws when DELETE query modifiers are invalid`(path: String) {
            shouldThrow<InvalidDeleteModifiersException> {
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
            shouldThrow<InvalidDeleteModifiersException> {
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
                "/assets/profile/-/created/10/",
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
                PathConfiguration.create(
                    allowedContentTypes = listOf("image/png"),
                    imageProperties = ImageProperties.DEFAULT,
                    eagerVariants = listOf(),
                    s3PathProperties = S3PathProperties.DEFAULT,
                )
            val context = requestContextFactory.fromStoreRequest(path, "image/png")

            context.pathConfiguration.allowedContentTypes shouldBe listOf("image/png")
            context.path shouldBe "/profile/123"
        }

        @Test
        fun `store asset path cannot have modifiers`() {
            val path = "/assets/profile/123/-/created"
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
                PathConfiguration.create(
                    allowedContentTypes = listOf("image/jpeg"),
                    imageProperties = ImageProperties.DEFAULT,
                    eagerVariants = listOf(),
                    s3PathProperties = S3PathProperties.DEFAULT,
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
            val path = "/assets/profile/123/-/metadata/entry/1"

            val exception =
                shouldThrow<InvalidPathException> {
                    requestContextFactory.fromUpdateRequest(path)
                }
            exception.message shouldBe "Return format cannot be supplied on update request"
        }

        @Test
        fun `cannot specify orderBy`() {
            val path = "/assets/profile/123/-/created/entry/1"

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
