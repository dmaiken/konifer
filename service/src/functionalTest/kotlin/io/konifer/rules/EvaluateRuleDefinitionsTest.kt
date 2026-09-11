package io.konifer.rules

import io.konifer.BaseLocalstackTestContainersTest
import io.konifer.ImageFactory
import io.konifer.KoniferTestHandle
import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.HttpSource
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.common.http.S3Source
import io.konifer.infrastructure.objectstore.s3.AwsS3SourceReader
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemoryHandle
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.dsl.module
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EvaluateRuleDefinitionsTest : BaseLocalstackTestContainersTest() {
    private lateinit var handle: KoniferTestHandle

    @BeforeAll
    fun startKonifer() {
        handle =
            testInMemoryHandle(
                """
                api.rule-evaluation.enabled = true
                
                # Test that this works even with url-signing enabled
                url-signing {
                    enabled = true
                    secret-key = secret
                }
                source = {
                  url = {
                    allowed-domains = [ konifer.io ]
                  }
                }
                """.trimIndent(),
                modules =
                    listOf(
                        module {
                            single<AwsS3SourceReader> {
                                AwsS3SourceReader(lazy { createLocalstackS3Client() })
                            }
                        },
                    ),
            )
        handle.start()
    }

    @AfterAll
    fun stopKonifer() {
        handle.close()
    }

    @Test
    fun `can evaluate one rule definition that matches`() =
        handle.test {
            val (image, attributes) = ImageFactory.testImage()

            val response =
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
                                                    "a tree",
                                                    "joshua tree national park",
                                                ),
                                            threshold = 0.7,
                                        ),
                                    ),
                            ),
                    ).shouldBeSuccessful()
                    .body

            response.results shouldHaveSize 1
            response.results.forExactly(1) {
                it.name shouldBe "one"
                it.matched shouldBe true
                it.threshold shouldBe 0.7
                it.score shouldBeGreaterThan 0.7
                it.promptScores.forAll { evaluatedPrompt ->
                    evaluatedPrompt.score shouldBeGreaterThan 0.0
                    evaluatedPrompt.score shouldBeLessThan 1.0
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a joshua tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "joshua tree national park"
                }
                it.promptScores shouldHaveSize 3
            }
        }

    @Test
    fun `can evaluate one rule definition for content supplied by url`() {
        handle.test {
            val response =
                konifer()
                    .evaluateRules(
                        request =
                            EvaluateRuleDefinitionsRequest(
                                definitions =
                                    listOf(
                                        RuleDefinitionRequest(
                                            name = "one",
                                            prompts =
                                                listOf(
                                                    "a green tree logo",
                                                    "a tree",
                                                    "a green logo",
                                                ),
                                            threshold = 0.6,
                                        ),
                                    ),
                                source =
                                    AssetSourceRequest(
                                        http = HttpSource(url = "https://konifer.io/img/konifer-small.png"),
                                    ),
                            ),
                    ).shouldBeSuccessful()
                    .body

            response.results shouldHaveSize 1
            response.results.forExactly(1) {
                it.name shouldBe "one"
                it.matched shouldBe true
                it.threshold shouldBe 0.6
                it.score shouldBeGreaterThan 0.6
                it.promptScores.forAll { evaluatedPrompt ->
                    evaluatedPrompt.score shouldBeGreaterThan 0.0
                    evaluatedPrompt.score shouldBeLessThan 1.0
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a green tree logo"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a green logo"
                }
                it.promptScores shouldHaveSize 3
            }
        }
    }

    @Test
    fun `can evaluate one rule definition for content supplied by arn`() {
        val bucket = "external-assets"
        val key = "images/joshua-tree.png"
        val arn = "arn:aws:s3:::$bucket/$key"
        val (image, attributes) = ImageFactory.testImage()
        createLocalstackS3Client().use { s3Client ->
            s3Client
                .createBucket(CreateBucketRequest.builder().bucket(bucket).build())
                .join()
            s3Client
                .putObject(
                    PutObjectRequest
                        .builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(attributes.format.mimeType)
                        .build(),
                    AsyncRequestBody.fromBytes(image),
                ).join()
            handle.test {
                val response =
                    konifer()
                        .evaluateRules(
                            request =
                                EvaluateRuleDefinitionsRequest(
                                    definitions =
                                        listOf(
                                            RuleDefinitionRequest(
                                                name = "one",
                                                prompts =
                                                    listOf(
                                                        "a joshua tree",
                                                        "a tree",
                                                        "joshua tree national park",
                                                    ),
                                                threshold = 0.7,
                                            ),
                                        ),
                                    source =
                                        AssetSourceRequest(
                                            s3 = S3Source(arn = arn),
                                        ),
                                ),
                        ).shouldBeSuccessful()
                        .body

                response.results shouldHaveSize 1
                response.results.forExactly(1) {
                    it.name shouldBe "one"
                    it.matched shouldBe true
                    it.threshold shouldBe 0.7
                    it.score shouldBeGreaterThan 0.7
                    it.promptScores.forAll { evaluatedPrompt ->
                        evaluatedPrompt.score shouldBeGreaterThan 0.0
                        evaluatedPrompt.score shouldBeLessThan 1.0
                    }
                    it.promptScores.forExactly(1) { evaluatedPrompt ->
                        evaluatedPrompt.prompt shouldBe "a tree"
                    }
                    it.promptScores.forExactly(1) { evaluatedPrompt ->
                        evaluatedPrompt.prompt shouldBe "a joshua tree"
                    }
                    it.promptScores.forExactly(1) { evaluatedPrompt ->
                        evaluatedPrompt.prompt shouldBe "joshua tree national park"
                    }
                    it.promptScores shouldHaveSize 3
                }
            }
        }
    }

    @Test
    fun `can evaluate one rule definition that does not match`() =
        handle.test {
            val (image, attributes) = ImageFactory.testImage()

            val response =
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
                                                    "a tree",
                                                    "joshua tree national park",
                                                ),
                                            threshold = 0.99,
                                        ),
                                    ),
                            ),
                    ).shouldBeSuccessful()
                    .body

            response.results shouldHaveSize 1
            response.results.forExactly(1) {
                it.name shouldBe "one"
                it.matched shouldBe false
                it.threshold shouldBe 0.99
                it.score shouldBeLessThan 0.99
                it.promptScores.forAll { evaluatedPrompt ->
                    evaluatedPrompt.score shouldBeGreaterThan 0.0
                    evaluatedPrompt.score shouldBeLessThan 1.0
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a joshua tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "joshua tree national park"
                }
                it.promptScores shouldHaveSize 3
            }
        }

    @Test
    fun `can evaluate multiple rule definitions`() =
        handle.test {
            val (image, attributes) = ImageFactory.testImage()

            val response =
                konifer()
                    .evaluateRules(
                        format = attributes.format,
                        bytes = image,
                        request =
                            EvaluateRuleDefinitionsRequest(
                                definitions =
                                    listOf(
                                        RuleDefinitionRequest(
                                            name = "matches",
                                            prompts =
                                                listOf(
                                                    "a joshua tree",
                                                    "a tree",
                                                    "joshua tree national park",
                                                ),
                                            threshold = 0.7,
                                        ),
                                        RuleDefinitionRequest(
                                            name = "does not match",
                                            prompts =
                                                listOf(
                                                    "a maple tree",
                                                ),
                                            threshold = 0.7,
                                        ),
                                    ),
                            ),
                    ).shouldBeSuccessful()
                    .body

            response.results shouldHaveSize 2
            response.results.forExactly(1) {
                it.name shouldBe "matches"
                it.matched shouldBe true
                it.threshold shouldBe 0.7
                it.score shouldBeGreaterThan 0.7
                it.promptScores.forAll { evaluatedPrompt ->
                    evaluatedPrompt.score shouldBeGreaterThan 0.0
                    evaluatedPrompt.score shouldBeLessThan 1.0
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a joshua tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "joshua tree national park"
                }
                it.promptScores shouldHaveSize 3
            }
            response.results.forExactly(1) {
                it.name shouldBe "does not match"
                it.matched shouldBe false
                it.threshold shouldBe 0.7
                it.score shouldBeLessThan 0.7
                it.promptScores.forAll { evaluatedPrompt ->
                    evaluatedPrompt.score shouldBeGreaterThan 0.0
                    evaluatedPrompt.score shouldBeLessThan 1.0
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a maple tree"
                }
                it.promptScores shouldHaveSize 1
            }
        }

    @Test
    fun `cannot submit request with no prompts`() =
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
                                        name = "matches",
                                        prompts = emptyList(),
                                        threshold = 0.7,
                                    ),
                                ),
                        ),
                ) shouldHaveHttpError 400
        }

    @Test
    fun `cannot submit with invalid threshold`() =
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
                                        name = "matches",
                                        prompts = listOf("prompt"),
                                        threshold = 1.01,
                                    ),
                                ),
                        ),
                ) shouldHaveHttpError 400
        }

    @Test
    fun `cannot submit with more prompts than allowed`() =
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
                                        name = "matches",
                                        prompts =
                                            buildList {
                                                repeat(101) {
                                                    add("prompt-$it")
                                                }
                                            },
                                        threshold = 0.7,
                                    ),
                                ),
                        ),
                ) shouldHaveHttpError 400
        }
}
