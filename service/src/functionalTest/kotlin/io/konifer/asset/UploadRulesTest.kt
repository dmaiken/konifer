package io.konifer.asset

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.KoniferTestHandle
import io.konifer.TestImageType
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemoryHandle
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UploadRulesTest : BaseFunctionalTest() {
    private lateinit var handle: KoniferTestHandle

    @BeforeAll
    fun startKonifer() {
        handle =
            testInMemoryHandle(
                """
                rule-definitions {
                    joshua-tree {
                        prompts = [
                          "a joshua tree",
                          "a tree",
                          "joshua tree national park"
                        ]
                        threshold = 0.7
                    }
                    joshua-tree-strict {
                        prompts = [
                          "a joshua tree",
                          "a tree",
                          "joshua tree national park"
                        ]
                        threshold = 0.71
                    }
                    kermit-the-frog {
                        prompts = [
                          "Kermit the frog",
                          "frog",
                          "green muppet",
                          "Kermit typing on a typewriter"
                        ]
                        threshold = 0.70
                    }
                }
                paths {
                  "/accept/**" {
                    upload-ruleset {
                      default = reject
                      accept-rules = [
                        { rule = joshua-tree }
                      ]
                    }
                  }
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
                          r = 180
                          w = 200
                        }
                      }
                    }
                  }
                  "/reject/**" {
                    upload-ruleset {
                      default = accept
                      reject-rules = [
                        { 
                          rule = joshua-tree 
                          violation-response = "No images of Joshua trees!"
                        }
                      ]
                    }
                  }
                  "/multiple-reject/**" {
                    upload-ruleset {
                      default = accept
                      reject-rules = [
                        { 
                          rule = joshua-tree 
                          violation-response = "No images of Joshua trees!"
                        }
                        { 
                          rule = joshua-tree-strict
                          violation-response = "This better not be a Joshua tree!"
                        }
                      ]
                    }
                  }
                  "/reject-all/**" {
                    upload-ruleset {
                      default = reject
                    }
                  }
                  "/accept-all/**" {
                    upload-ruleset {
                      default = accept
                    }
                  }
                  "/joshua-tree-label/**" {
                    upload-ruleset {
                      default = accept
                      label-rules = [
                        { 
                          rule = joshua-tree
                          labels = {
                            "tree" = "joshua tree"
                            "climate" = "desert"
                          }
                        }
                      ]
                    }
                  }
                  "/joshua-tree-label/with-preprocessing/**" {
                    transform {
                      preprocessing {
                        enabled = true
                        image {
                          r = 180
                          w = 200
                        }
                      }
                    }
                  }
                  "/joshua-tree-label-default-accept-with-accept-rules/**" {
                    upload-ruleset {
                      default = accept
                      label-rules = [
                        { 
                          rule = joshua-tree
                          labels = {
                            "tree" = "joshua tree"
                            "climate" = "desert"
                          }
                        }
                      ]
                      accept-rules = [
                        { rule = joshua-tree-strict }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            )
        handle.start()
    }

    @AfterAll
    fun stopKonifer() {
        handle.close()
    }

    @ParameterizedTest
    @EnumSource(ImageFormat::class)
    fun `accept rule allows matching images in every format`(format: ImageFormat) =
        handle.test {
            val (image, attributes) =
                ImageFactory.testImage(
                    type = TestImageType.JOSHUA_TREE,
                    format = format,
                )
            konifer()
                .storeAsset(
                    path = "accept/${format.format}",
                    bytes = image,
                    format = attributes.format,
                    request = StoreAssetRequest(),
                ).shouldBeSuccessful()
        }

    @ParameterizedTest
    @MethodSource("io.konifer.ImageTestSources#supportsPagedSource")
    fun `accept rule allows matching images in every multipage format`(format: ImageFormat) =
        handle.test {
            val (image, attributes) =
                ImageFactory.testImage(
                    type = TestImageType.KERMIT,
                    format = format,
                )
            konifer()
                .storeAsset(
                    path = "kermit-accept/${format.format}",
                    bytes = image,
                    format = attributes.format,
                    request = StoreAssetRequest(),
                ).shouldBeSuccessful()
        }

    @ParameterizedTest
    @MethodSource("io.konifer.ImageTestSources#supportsPagedSource")
    fun `can accept and preprocess multi-page image`(format: ImageFormat) =
        handle.test {
            val (image, attributes) =
                ImageFactory.testImage(
                    type = TestImageType.KERMIT,
                    format = format,
                )
            val response =
                konifer()
                    .storeAsset(
                        path = "kermit-accept/with-preprocessing/${format.format}",
                        bytes = image,
                        format = attributes.format,
                        request = StoreAssetRequest(),
                    ).shouldBeSuccessful()
                    .body

            response.variants shouldHaveSize 1
            with(response.variants.first()) {
                this.attributes.format shouldBe format.format
                this.attributes.width shouldBe 200
                // Ensure paging is handled correctly since rule evaluation generated a single-page version
                // The version generated for rule evaluation should be copied
                (this.attributes.pageCount ?: 1) shouldBeGreaterThan 1
            }
        }

    @Test
    fun `accept rule rejects images that do not match`() =
        handle.test {
            val (moonImage, moonAttributes) = ImageFactory.testImage(type = TestImageType.MOON, format = ImageFormat.PNG)
            val error =
                konifer()
                    .storeAsset(
                        path = "accept/moon",
                        bytes = moonImage,
                        format = moonAttributes.format,
                        request = StoreAssetRequest(),
                    ).shouldHaveHttpError(400)

            error.message shouldBe "Asset rejected by upload rules"
        }

    @Test
    fun `reject rule rejects matching images`() =
        handle.test {
            val (joshuaTreeImage, joshuaTreeAttributes) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE)
            val error =
                konifer()
                    .storeAsset(
                        path = "reject/joshua-tree",
                        bytes = joshuaTreeImage,
                        format = joshuaTreeAttributes.format,
                        request = StoreAssetRequest(),
                    ).shouldHaveHttpError(400)

            error.message shouldBe "No images of Joshua trees!"
        }

    @Test
    fun `multiple reject rules return all violation responses`() =
        handle.test {
            val (joshuaTreeImage, joshuaTreeAttributes) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE)
            val error =
                konifer()
                    .storeAsset(
                        path = "multiple-reject/joshua-tree",
                        bytes = joshuaTreeImage,
                        format = joshuaTreeAttributes.format,
                        request = StoreAssetRequest(),
                    ).shouldHaveHttpError(400)

            error.message shouldBe "No images of Joshua trees!; This better not be a Joshua tree!"
        }

    @Test
    fun `default reject with no rules rejects everything`() =
        handle.test {
            val (joshuaTreeImage, joshuaTreeAttributes) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE)
            val error =
                konifer()
                    .storeAsset(
                        path = "reject-all/joshua-tree",
                        bytes = joshuaTreeImage,
                        format = joshuaTreeAttributes.format,
                        request = StoreAssetRequest(),
                    ).shouldHaveHttpError(400)

            error.message shouldBe "Asset rejected by upload rules"
        }

    @Test
    fun `default accept with no rules accepts everything`() =
        handle.test {
            val (joshuaTreeImage, joshuaTreeAttributes) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE)
            konifer()
                .storeAsset(
                    path = "accept-all/joshua-tree",
                    bytes = joshuaTreeImage,
                    format = joshuaTreeAttributes.format,
                    request = StoreAssetRequest(),
                ).shouldBeSuccessful()
        }

    @Test
    fun `label rules apply labels when rule is matched`() =
        handle.test {
            val (joshuaTreeImage, joshuaTreeAttributes) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE)
            val response =
                konifer()
                    .storeAsset(
                        path = "joshua-tree-label/joshua-tree",
                        bytes = joshuaTreeImage,
                        format = joshuaTreeAttributes.format,
                        request = StoreAssetRequest(),
                    ).shouldBeSuccessful()

            response.body.labels shouldBe mapOf("tree" to "joshua tree", "climate" to "desert")
        }

    @Test
    fun `label rules apply labels when rule is matched during preprocessing`() =
        handle.test {
            val (joshuaTreeImage, joshuaTreeAttributes) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE)
            val response =
                konifer()
                    .storeAsset(
                        path = "joshua-tree-label/with-preprocessing/joshua-tree",
                        bytes = joshuaTreeImage,
                        format = joshuaTreeAttributes.format,
                        request = StoreAssetRequest(),
                    ).shouldBeSuccessful()

            response.body.labels shouldBe mapOf("tree" to "joshua tree", "climate" to "desert")
            response.body.variants
                .first()
                .attributes.width shouldBe 200
        }

    @Test
    fun `label rules are merged with request labels when rule is matched`() =
        handle.test {
            val (joshuaTreeImage, joshuaTreeAttributes) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE)
            val response =
                konifer()
                    .storeAsset(
                        path = "joshua-tree-label/joshua-tree-with-request-labels",
                        bytes = joshuaTreeImage,
                        format = joshuaTreeAttributes.format,
                        request =
                            StoreAssetRequest(
                                labels =
                                    mapOf(
                                        "tree" to "generic tree",
                                        "source" to "request",
                                    ),
                            ),
                    ).shouldBeSuccessful()

            response.body.labels shouldBe
                mapOf(
                    "tree" to "joshua tree",
                    "climate" to "desert",
                    "source" to "request",
                )
        }

    @Test
    fun `if only rules align with default then labels are not applied if rule not matched`() =
        handle.test {
            val (kermitImage, kermitAttributes) = ImageFactory.testImage(type = TestImageType.KERMIT, format = ImageFormat.WEBP)
            val response =
                konifer()
                    .storeAsset(
                        path = "joshua-tree-label-default-accept-with-accept-rules",
                        bytes = kermitImage,
                        format = kermitAttributes.format,
                        request =
                            StoreAssetRequest(),
                    ).shouldBeSuccessful()
            response.body.labels shouldBe emptyMap()
        }

    /**
     * Test that even if Upload Rules are configured, the Rule Evaluation API is still
     * disabled unless enabled through configuration
     */
    @Test
    fun `rule evaluation API is still disabled unless explicitly enabled`() =
        handle.test {
            val (image, attributes) = ImageFactory.testImage()

            konifer()
                .evaluateRules(
                    format = attributes.format,
                    bytes = image,
                    request =
                        EvaluateRuleDefinitionsRequest(
                            definitions =
                                listOf(
                                    RuleDefinitionRequest(
                                        name = "one",
                                        prompts =
                                            listOf(
                                                "a joshua tree",
                                            ),
                                        threshold = 0.99,
                                    ),
                                ),
                        ),
                ).shouldHaveHttpError(404)
        }
}
