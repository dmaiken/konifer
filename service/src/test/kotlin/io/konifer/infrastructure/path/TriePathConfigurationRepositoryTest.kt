package io.konifer.infrastructure.path

import com.typesafe.config.ConfigFactory
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.rules.RuleName
import io.konifer.domain.rules.upload.DefaultRuleAction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TriePathConfigurationRepositoryTest {
    @Test
    fun `fetch returns a path configuration when the path matches exactly`() {
        val config =
            """
            paths {
              "/users/123/profile" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
              "/users/456/profile" {
                allowed-content-types = [
                  "image/jpeg"
                ]
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/users/123/profile")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @Test
    fun `fetch returns a path configuration when the path matches exactly but case does not`() {
        val config =
            """
            paths = {
              "/Users/123/Profile" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
              "/users/456/profile" {
                allowed-content-types = [
                  "image/jpeg"
                ]
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
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
            paths {
              "/users/*/profile" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/users/123/profile")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @Test
    fun `fetch returns a path configuration when the path matcher has double wildcard`() {
        val config =
            """
            paths {
              "/users/**" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
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
            paths {
              "$path" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
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
            paths {
              "$path" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration =
            pathConfigurationRepository.fetch("/users/lastName/firstName/profile/last")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @Test
    fun `path configuration is inherited if not supplied`() {
        val config =
            """
            paths {
              "/users/*" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
                transform {
                  preprocessing = {
                    image {
                      max-height = 10
                    }
                  }
                }
              }
              "/users/*/profile" {
                allowed-content-types = [ ]
                transform {
                  preprocessing = {
                    image {
                      max-width = 10
                    }
                  }
                }
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/users/123/profile")
        pathConfiguration.allowedContentTypes shouldBe listOf()
        pathConfiguration.transform.preProcessing.image.maxWidth shouldBe 10
        pathConfiguration.transform.preProcessing.image.maxHeight shouldBe 10
    }

    @Test
    fun `default path is used when none suffice`() {
        val config =
            """
            paths {
              "/**" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
              "/users/**" {
                allowed-content-types = [ ]
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/recipe/123")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @Test
    fun `default path configuration is inherited`() {
        val config =
            """
            paths {
              "/**" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
                transform {
                  preprocessing = {
                    image {
                      max-height = 10
                    }
                  }
                }
              }
              "/users/*/profile" {
                allowed-content-types = [ ]
                transform {
                  preprocessing = {
                    image {
                      max-width = 10
                    }
                  }
                }
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/users/123/profile")
        pathConfiguration.allowedContentTypes shouldBe listOf()
        pathConfiguration.transform.preProcessing.image.maxWidth shouldBe 10
        pathConfiguration.transform.preProcessing.image.maxHeight shouldBe 10
    }

    @Test
    fun `path is stripped of blank and empty path segments`() {
        val config =
            """
            paths {
              "/**" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("// //123")
        pathConfiguration.allowedContentTypes shouldBe listOf("image/png", "image/jpeg")
    }

    @Test
    fun `path must be supplied`() {
        val config =
            """
            paths {
              "" {
                allowed-content-types = [
                  "image/png",
                  "image/jpeg"
                ]
              }
            }
            """.trimIndent()
        shouldThrow<IllegalArgumentException> {
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        }.message shouldBe "Path key cannot be blank"
    }

    @Test
    fun `eager variants are parsed`() {
        val config =
            """
            paths {
              "/**" {
                transform {
                  eager-variants = [small, large]
                }
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/profile")
        pathConfiguration.transform.eagerVariants shouldBe listOf("small", "large")
    }

    @Test
    fun `eager variants override parent paths`() {
        val config =
            """
            paths {
              "/**" {
                transform {
                  eager-variants = [small, large]
                }
              }
              "/profile/*" {
                transform {
                  eager-variants = [large]
                }
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch("/profile/123")
        pathConfiguration.transform.eagerVariants shouldBe listOf("large")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/", "/profile/", "/profile/123/"])
    fun `configuration under wildcard is applied at the root of the path`(path: String) {
        val config =
            """
            paths {
              "$path*" {
                transform {
                  eager-variants = [large]
                }
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch(path)
        pathConfiguration.transform.eagerVariants shouldBe listOf("large")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/", "/profile/", "/profile/123/"])
    fun `configuration under greedy wildcard is applied at the root of the path`(path: String) {
        val config =
            """
            paths {
              "$path**" {
                transform {
                  eager-variants = [large]
                }
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch(path)
        pathConfiguration.transform.eagerVariants shouldBe listOf("large")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/profile", "/profile/123"])
    fun `wildcard wins over greedy wildcard specified on same path`(path: String) {
        val config =
            """
            paths {
              "/profile/*" {
                transform {
                  eager-variants = [medium]
                }
              }
              "/profile/**" {
                transform {
                  eager-variants = [large]
                }
              }
            }
            """.trimIndent()
        val pathConfigurationRepository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )
        val pathConfiguration = pathConfigurationRepository.fetch(path)
        pathConfiguration.transform.eagerVariants shouldBe listOf("medium")
    }

    @Test
    fun `exact path wins over wildcard on same path`() {
        val config =
            """
            paths {
              "/profile/123" {
                transform {
                  eager-variants = [small]
                }
              }
              "/profile/*" {
                transform {
                  eager-variants = [large]
                }
              }
            }
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )

        repository.fetch("/profile/123").transform.eagerVariants shouldBe listOf("small")
    }

    @Test
    fun `exact path wins over greedy wildcard on same path`() {
        val config =
            """
            paths {
              "/profile/123" {
                transform {
                  eager-variants = [small]
                }
              }
              "/profile/**" {
                transform {
                  eager-variants = [large]
                }
              }
            }
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )

        repository.fetch("/profile/123").transform.eagerVariants shouldBe listOf("small")
    }

    @Test
    fun `explicit parent path wins over wildcard child when fetching parent path`() {
        val config =
            """
            paths {
              "/profile" {
                transform {
                  eager-variants = [small]
                }
              }
              "/profile/*" {
                transform {
                  eager-variants = [large]
                }
              }
            }
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )

        repository.fetch("/profile").transform.eagerVariants shouldBe listOf("small")
    }

    @Test
    fun `greedy wildcard can consume zero segments before matching following segment`() {
        val config =
            """
            paths {
              "/users/**/profile" {
                transform {
                  eager-variants = [large]
                }
              }
            }
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )

        repository.fetch("/users/profile").transform.eagerVariants shouldBe listOf("large")
    }

    @Test
    fun `deeper greedy wildcard match wins over shallower greedy wildcard match`() {
        val config =
            """
            paths {
              "/users/**" {
                transform {
                  eager-variants = [small]
                }
              }
              "/users/**/profile" {
                transform {
                  eager-variants = [large]
                }
              }
            }
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )

        repository.fetch("/users/123/profile").transform.eagerVariants shouldBe listOf("large")
    }

    @Test
    fun `matching wildcard configurations are merged in specificity order`() {
        val config =
            """
            paths {
              "/kermit-accept/**" {
                upload-ruleset {
                  default = reject
                  accept-rules = [
                    { rule = kermit-the-frog }
                  ]
                }
              }
              "/kermit-accept/with-preprocessing/**" {
                transform {
                  preprocessing {
                    enabled = true
                    image {
                      w = 200
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )

        val pathConfiguration = repository.fetch("/kermit-accept/with-preprocessing/tiff")

        pathConfiguration.uploadRuleset.default shouldBe DefaultRuleAction.REJECT
        pathConfiguration.uploadRuleset.acceptRules
            .single()
            .rule shouldBe RuleName("kermit-the-frog")
        pathConfiguration.transform.preProcessing.enabled shouldBe true
        pathConfiguration.transform.preProcessing.image.width shouldBe 200
    }

    @Test
    fun `configured path is stripped of blank and empty path segments`() {
        val config =
            """
            paths {
              "//profile//*/" {
                transform {
                  eager-variants = [large]
                }
              }
            }
            """.trimIndent()

        val repository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )

        repository.fetch("/profile/123").transform.eagerVariants shouldBe listOf("large")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "paths { }"])
    fun `default is configured when no paths are specified`(config: String) {
        val repository =
            TriePathConfigurationRepository(
                ConfigFactory.parseString(config),
            )

        repository.fetch("/users/123/profile") shouldBe PathConfiguration.default
    }
}
