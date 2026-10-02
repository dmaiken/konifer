package integration

import io.konifer.clientV2.KoniferV2Result
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
                clientV2
                    .ruleEvaluation()
                    .fromBytes(image, format)
                    .withDefinition(
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
                    ).evaluate()
            storeResponse::class shouldBe KoniferV2Result.Success::class

            val body = (storeResponse as KoniferV2Result.Success).value
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
