package integration

import io.konifer.client.KoniferResponse
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.common.image.ImageFormat
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class RuleEvaluationIntegrationTest : BaseIntegrationTest() {
    @ParameterizedTest
    @EnumSource(value = ImageFormat::class)
    fun `can evaluate rules against content that matches`(format: ImageFormat) {
        runBlocking {
            val (image, _) = ImageFactory.testImage(type = TestImageType.JOSHUA_TREE, format = format)
            val storeResponse =
                client.evaluateRules(
                    format = format,
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
                )
            storeResponse::class shouldBe KoniferResponse.Success::class

            val body = (storeResponse as KoniferResponse.Success).body
            body.results shouldHaveSize 1
            body.results.forExactly(1) {
                it.name shouldBe "one"
                it.promptScores shouldHaveSize 3
                it.matched shouldBe true
                it.threshold shouldBe 0.7
                it.score shouldBeGreaterThan 0.7
            }
        }
    }
}
