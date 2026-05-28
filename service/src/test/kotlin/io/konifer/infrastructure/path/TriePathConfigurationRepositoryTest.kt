package io.konifer.infrastructure.path

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.server.config.HoconApplicationConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TriePathConfigurationRepositoryTest {
    @Test
    fun `fetch returns a path configuration when the path matches exactly`() {
        val config =
            """
            paths = [
              {
                path = "/users/123/profile"
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              },
              {
                path = "/users/456/profile"
                allowed-content-types = [
                  "image/jpeg"
                ]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/users/123/profile")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @Test
    fun `fetch returns a path configuration when the path matches exactly but case does not`() {
        val config =
            """
            paths = [
              {
                path = "/Users/123/Profile"
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              },
              {
                path = "/users/456/profile"
                allowed-content-types = [
                  "image/jpeg"
                ]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        listOf(
            "/users/123/profile",
            "/USERS/123/profile",
        ).forEach { path ->
            val pathConfiguration = pathConfigurationRepository.fetch(path)
            pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
        }
    }

    @Test
    fun `fetch returns a path configuration when the path matcher has single wildcard`() {
        val config =
            """
            paths = [
              {
                path = "/users/*/profile"
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/users/123/profile")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @Test
    fun `fetch returns a path configuration when the path matcher has double wildcard`() {
        val config =
            """
            paths = [
              {
                path = "/users/**"
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/users/123/profile")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/users/123/profile",
            "/users/*/profile",
            "/users/**",
        ],
    )
    fun `fetch does not return a path configuration when the path matcher does not match`(path: String) {
        val config =
            """
            paths = [
              {
                path = "$path"
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        pathConfigurationRepository.fetch("/notAUser/123/profile").apply {
            allowedContentTypes shouldBe null
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/users/**/profile/**",
            "/users/**",
            "/users/**/profile/**/last",
        ],
    )
    fun `greedy wildcard matching works`(path: String) {
        val config =
            """
            paths = [
              {
                path = "$path"
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration =
            pathConfigurationRepository.fetch("/users/lastName/firstName/profile/last")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @Test
    fun `path configuration is inherited if not supplied`() {
        val config =
            """
            paths = [
              {
                path = "/users/*"
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ],
                preprocessing = {
                  image {
                    max-height = 10
                  }
                }
              },
              {
                path = "/users/*/profile"
                allowed-content-types = [ ]
                preprocessing = {
                  image {
                    max-width = 10
                  }
                }
              },
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/users/123/profile")
        pathConfiguration.allowedContentTypes shouldBe listOf()
        pathConfiguration.preProcessing.image.maxWidth shouldBe 10
        pathConfiguration.preProcessing.image.maxHeight shouldBe 10
    }

    @Test
    fun `default path is used when none suffice`() {
        val config =
            """
            paths = [
              {
                path = "/**"
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              },
              {
                path = "/users/**"
                allowed-content-types = [ ]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/recipe/123")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @Test
    fun `default path configuration is inherited`() {
        val config =
            """
            paths = [
              {
                path = "/**"
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ],
                preprocessing = {
                  image {
                    max-height = 10
                  }
                }
              },
              {
                path = "/users/*/profile"
                allowed-content-types = [ ]
                preprocessing = {
                  image {
                    max-width = 10
                  }
                }
              },
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/users/123/profile")
        pathConfiguration.allowedContentTypes shouldBe listOf()
        pathConfiguration.preProcessing.image.maxWidth shouldBe 10
        pathConfiguration.preProcessing.image.maxHeight shouldBe 10
    }

    @Test
    fun `path is stripped of blank and empty path segments`() {
        val config =
            """
            paths = [
              {
                path = "/**"
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("// //123")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @Test
    fun `path must be supplied`() {
        val config =
            """
            paths = [
              {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            ]
            """.trimIndent()
        shouldThrow<IllegalArgumentException> {
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        }.message shouldBe "Path configuration must be supplied"
    }

    @Test
    fun `eager variants are parsed`() {
        val config =
            """
            paths = [
              {
                path = "/**"
                eager-variants = [small, large]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/profile")
        pathConfiguration.eagerVariants shouldBe listOf("small", "large")
    }

    @Test
    fun `eager variants override parent paths`() {
        val config =
            """
            paths = [
              {
                path = "/**"
                eager-variants = [small, large]
              },
              {
                path = "/profile/*"
                eager-variants = [large]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/profile/123")
        pathConfiguration.eagerVariants shouldBe listOf("large")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/", "/profile/", "/profile/123/"])
    fun `configuration under wildcard is applied at the root of the path`(path: String) {
        val config =
            """
            paths = [
              {
                path = "$path*"
                eager-variants = [large]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch(path)
        pathConfiguration.eagerVariants shouldBe listOf("large")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/", "/profile/", "/profile/123/"])
    fun `configuration under greedy wildcard is applied at the root of the path`(path: String) {
        val config =
            """
            paths = [
              {
                path = "$path**"
                eager-variants = [large]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch(path)
        pathConfiguration.eagerVariants shouldBe listOf("large")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/profile", "/profile/123"])
    fun `wildcard wins over greedy wildcard specified on same path`(path: String) {
        val config =
            """
            paths = [
              {
                path = "/profile/*"
                eager-variants = [medium]
              }
              {
                path = "/profile/**"
                eager-variants = [large]
              }
            ]
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )
        val pathConfiguration = pathConfigurationRepository.fetch(path)
        pathConfiguration.eagerVariants shouldBe listOf("medium")
    }

    @Test
    fun `exact path wins over wildcard on same path`() {
        val config =
            """
            paths = [
              {
                path = "/profile/123"
                eager-variants = [small]
              },
              {
                path = "/profile/*"
                eager-variants = [large]
              }
            ]
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )

        repository.fetch("/profile/123").eagerVariants shouldBe listOf("small")
    }

    @Test
    fun `exact path wins over greedy wildcard on same path`() {
        val config =
            """
            paths = [
              {
                path = "/profile/123"
                eager-variants = [small]
              },
              {
                path = "/profile/**"
                eager-variants = [large]
              }
            ]
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )

        repository.fetch("/profile/123").eagerVariants shouldBe listOf("small")
    }

    @Test
    fun `explicit parent path wins over wildcard child when fetching parent path`() {
        val config =
            """
            paths = [
              {
                path = "/profile"
                eager-variants = [small]
              },
              {
                path = "/profile/*"
                eager-variants = [large]
              }
            ]
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )

        repository.fetch("/profile").eagerVariants shouldBe listOf("small")
    }

    @Test
    fun `greedy wildcard can consume zero segments before matching following segment`() {
        val config =
            """
            paths = [
              {
                path = "/users/**/profile"
                eager-variants = [large]
              }
            ]
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )

        repository.fetch("/users/profile").eagerVariants shouldBe listOf("large")
    }

    @Test
    fun `deeper greedy wildcard match wins over shallower greedy wildcard match`() {
        val config =
            """
            paths = [
              {
                path = "/users/**"
                eager-variants = [small]
              },
              {
                path = "/users/**/profile"
                eager-variants = [large]
              }
            ]
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )

        repository.fetch("/users/123/profile").eagerVariants shouldBe listOf("large")
    }

    @Test
    fun `configured path is stripped of blank and empty path segments`() {
        val config =
            """
            paths = [
              {
                path = "//profile//*/"
                eager-variants = [large]
              }
            ]
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                HoconApplicationConfig(ConfigFactory.parseString(config)),
            )

        repository.fetch("/profile/123").eagerVariants shouldBe listOf("large")
    }
}
