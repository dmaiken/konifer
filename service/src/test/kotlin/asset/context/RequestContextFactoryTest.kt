package io.asset.context

import image.model.RequestedImageAttributes
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.mockk.every
import io.mockk.mockk
import io.path.DeleteMode
import io.path.configuration.PathConfiguration
import io.path.configuration.PathConfigurationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class RequestContextFactoryTest {
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
                    ),
                ),
                arguments(
                    "/assets/profile/-/link/created/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/redirect/created/1",
                    QueryModifiers(
                        returnFormat = ReturnFormat.REDIRECT,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/-/content/created/1",
                    QueryModifiers(
                        returnFormat = ReturnFormat.CONTENT,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/-/mEtAData/created/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
                        limit = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/created/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata/created",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
                        limit = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/-/created",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/-/",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/-",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        orderBy = OrderBy.CREATED,
                        limit = 1,
                    ),
                ),
                arguments(
                    "/assets/profile/-/metadata/created/10/",
                    QueryModifiers(
                        returnFormat = ReturnFormat.METADATA,
                        orderBy = OrderBy.CREATED,
                        limit = 10,
                    ),
                ),
            )

        @JvmStatic
        fun deleteModifierSource(): List<Arguments> =
            listOf(
                arguments(
                    "/assets/profile/-/children",
                    DeleteModifiers(
                        mode = DeleteMode.CHILDREN,
                    ),
                ),
                arguments(
                    "/assets/profile/-/recursive",
                    DeleteModifiers(
                        mode = DeleteMode.RECURSIVE,
                    ),
                ),
                arguments(
                    "/assets/profile/-/single",
                    DeleteModifiers(
                        mode = DeleteMode.SINGLE,
                    ),
                ),
                arguments(
                    "/assets/profile/-/",
                    DeleteModifiers(
                        mode = DeleteMode.SINGLE,
                    ),
                ),
                arguments(
                    "/assets/profile/",
                    DeleteModifiers(
                        mode = DeleteMode.SINGLE,
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
                    ),
                ),
                arguments(
                    "/assets/profile/-/content/entry/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.CONTENT,
                        entryId = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/redirect/entry/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.REDIRECT,
                        entryId = 10,
                    ),
                ),
                arguments(
                    "/assets/profile/-/link/entry/10",
                    QueryModifiers(
                        returnFormat = ReturnFormat.LINK,
                        entryId = 10,
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
        fun requestedImageAttributesSource(): List<Arguments> =
            listOf(
                arguments(
                    ParametersBuilder(3).apply {
                        append("w", "10")
                        append("h", "20")
                        append("mimeType", "image/png")
                    }.build(),
                    RequestedImageAttributes(
                        width = 10,
                        height = 20,
                        mimeType = "image/png",
                    ),
                ),
                arguments(
                    ParametersBuilder(2).apply {
                        append("w", "10")
                        append("h", "20")
                    }.build(),
                    RequestedImageAttributes(
                        width = 10,
                        height = 20,
                        mimeType = null,
                    ),
                ),
                arguments(
                    ParametersBuilder(1).apply {
                        append("w", "10")
                    }.build(),
                    RequestedImageAttributes(
                        width = 10,
                        height = null,
                        mimeType = null,
                    ),
                ),
                arguments(
                    ParametersBuilder(1).apply {
                        append("mimeType", "image/png")
                    }.build(),
                    RequestedImageAttributes(
                        width = null,
                        height = null,
                        mimeType = "image/png",
                    ),
                ),
            )
    }

    private val pathConfigurationService = mockk<PathConfigurationService>()
    private val requestContextFactory = RequestContextFactory(pathConfigurationService)

    @BeforeEach
    fun beforeEach() {
        every {
            pathConfigurationService.fetchConfigurationForPath(any())
        } returns PathConfiguration.DEFAULT
    }

    @ParameterizedTest
    @MethodSource("queryModifierSource")
    fun `can fetch GET request context with query modifiers`(
        path: String,
        queryModifiers: QueryModifiers,
    ) {
        val context = requestContextFactory.fromGetRequest(path, Parameters.Empty)

        context.pathConfiguration shouldBe PathConfiguration.DEFAULT
        context.modifiers shouldBe queryModifiers
    }

    @ParameterizedTest
    @MethodSource("deleteModifierSource")
    fun `can fetch DELETE request context with query modifiers`(
        path: String,
        deleteModifiers: DeleteModifiers,
    ) {
        val context = requestContextFactory.fromDeleteRequest(path)

        context.modifiers shouldBe deleteModifiers
    }

    @ParameterizedTest
    @MethodSource("getEntryIdSource")
    fun `can fetch GET request context with entryId`(
        path: String,
        queryModifiers: QueryModifiers,
    ) {
        val context = requestContextFactory.fromGetRequest(path, Parameters.Empty)

        context.pathConfiguration shouldBe PathConfiguration.DEFAULT
        context.modifiers shouldBe queryModifiers
    }

    @Test
    fun `can fetch DELETE request context with entryId`() {
        val context = requestContextFactory.fromDeleteRequest("/assets/profile/-/entry/10")

        context.modifiers shouldBe
            DeleteModifiers(
                entryId = 10,
            )
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
    fun `throws when GET query modifiers are invalid`(path: String) {
        shouldThrow<InvalidQueryModifiersException> {
            requestContextFactory.fromGetRequest(path, Parameters.Empty)
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/assets/profile/-/childrenn/",
            "/assets/profile/-/children/10",
            "/assets/profile/-/10/",
        ],
    )
    fun `throws when DELETE query modifiers are invalid`(path: String) {
        shouldThrow<InvalidDeleteModifiersException> {
            requestContextFactory.fromDeleteRequest(path)
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
    fun `entryId must be positive when fetching GET request context`(path: String) {
        shouldThrow<InvalidQueryModifiersException> {
            requestContextFactory.fromGetRequest(path, Parameters.Empty)
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
            requestContextFactory.fromDeleteRequest(path)
        }
    }

    @ParameterizedTest
    @EnumSource(value = ReturnFormat::class, mode = EnumSource.Mode.INCLUDE, names = ["REDIRECT", "CONTENT"])
    fun `cannot have limit greater than one with certain return formats`(returnFormat: ReturnFormat) {
        val exception =
            shouldThrow<InvalidQueryModifiersException> {
                requestContextFactory.fromGetRequest("/assets/profile/user/123/-/$returnFormat/2", Parameters.Empty)
            }

        exception.message shouldBe "Cannot have limit > 1 with return format of: ${returnFormat.name.lowercase()}"
    }

    @Test
    fun `path can only have one namespace separator in GET request context`() {
        val path = "/assets/profile/-/-/metadata/created/10/"
        val exception =
            shouldThrow<InvalidPathException> {
                requestContextFactory.fromGetRequest(path, Parameters.Empty)
            }
        exception.message shouldBe "$path has more than one '-' segment"
    }

    @Test
    fun `path can only have one namespace separator in DELETE request context`() {
        val path = "/assets/profile/-/-/single/"
        val exception =
            shouldThrow<InvalidPathException> {
                requestContextFactory.fromDeleteRequest(path)
            }
        exception.message shouldBe "$path has more than one '-' segment"
    }

    @ParameterizedTest
    @MethodSource("requestedImageAttributesSource")
    fun `can parse requested image attributes in GET request context`(
        parameters: Parameters,
        requestedImageAttributes: RequestedImageAttributes,
    ) {
        val path = "/assets/profile/-/link/created/10/"

        val context = requestContextFactory.fromGetRequest(path, parameters)
        context.requestedImageAttributes shouldBe requestedImageAttributes
    }

    @Test
    fun `cannot create GET context if requesting metadata with image attributes`() {
        val parameters =
            ParametersBuilder(3).apply {
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
    fun `can parse GET asset path from the uri request path`() {
        val context = requestContextFactory.fromGetRequest("/assets/profile/123/-/metadata/2", Parameters.Empty)

        context.path shouldBe "profile/123/"
    }

    @Test
    fun `can parse DELETE asset path from the uri request path`() {
        val context = requestContextFactory.fromDeleteRequest("/assets/profile/123/-/children")

        context.path shouldBe "profile/123/"
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
    fun `throws if GET uri path does not start with correct prefix`(path: String) {
        val exception =
            shouldThrow<InvalidPathException> {
                requestContextFactory.fromGetRequest(path, Parameters.Empty)
            }

        exception.message shouldBe "Asset path must start with: /assets"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/ASSETS/profile/-/children/",
            "/Assets/profile/-/children/",
            "/Asssetts/profile/-/children/",
            "/profile/-/children/",
        ],
    )
    fun `throws if DELETE uri path does not start with correct prefix`(path: String) {
        val exception =
            shouldThrow<InvalidPathException> {
                requestContextFactory.fromDeleteRequest(path)
            }

        exception.message shouldBe "Asset path must start with: /assets"
    }
}
