package io.konifer.infrastructure.rules

import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.asset.toAssetLabels
import io.konifer.domain.rules.EvaluationScore
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.domain.rules.RuleDefinitionsEvaluationResult
import io.konifer.domain.rules.RuleEvaluationResult
import io.konifer.domain.rules.RuleName
import io.konifer.domain.rules.RuleViolationResponse
import io.konifer.domain.rules.upload.DefaultRuleAction
import io.konifer.domain.rules.upload.UploadRule
import io.konifer.domain.rules.upload.UploadRuleset
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class RuleDecisionEngineTest {
    @ParameterizedTest
    @EnumSource(DefaultRuleAction::class)
    fun `returns default decision when nothing is evaluated`(default: DefaultRuleAction) {
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset = UploadRuleset(default = default),
                evaluationResult = RuleDefinitionsEvaluationResult(results = emptyList()),
            )

        when (default) {
            DefaultRuleAction.REJECT -> {
                result.accept shouldBe false
                result.violationResponses shouldHaveSize 0
                result.labels.asMap() shouldBe AssetLabels.empty.asMap()
            }
            DefaultRuleAction.ACCEPT -> {
                result.accept shouldBe true
                result.violationResponses shouldHaveSize 0
                result.labels.asMap() shouldBe AssetLabels.empty.asMap()
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DefaultRuleAction::class)
    fun `returns default decision with matched labels when no acceptance rules are evaluated`(default: DefaultRuleAction) {
        val labelRules =
            listOf(
                UploadRule(
                    rule = RuleName("phone"),
                    labels = mapOf("phone" to "iphone").toAssetLabels(),
                ),
            )
        val ruleDefinition =
            RuleDefinition(
                name = RuleName("phone"),
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset = UploadRuleset(default = default, labelRules = labelRules),
                evaluationResult =
                    RuleDefinitionsEvaluationResult(
                        results =
                            listOf(
                                RuleEvaluationResult(
                                    ruleDefinition = ruleDefinition,
                                    evaluationScore =
                                        EvaluationScore(
                                            score = 0.9,
                                            matched = true,
                                        ),
                                    promptScores = mapOf(),
                                ),
                            ),
                    ),
            )

        when (default) {
            DefaultRuleAction.REJECT -> {
                result.accept shouldBe false
                result.violationResponses shouldHaveSize 0
                result.labels.asMap() shouldBe mapOf("phone" to "iphone")
            }
            DefaultRuleAction.ACCEPT -> {
                result.accept shouldBe true
                result.violationResponses shouldHaveSize 0
                result.labels.asMap() shouldBe mapOf("phone" to "iphone")
            }
        }
    }

    @Test
    fun `returns accept decision when rules match`() {
        val ruleDefinition =
            RuleDefinition(
                name = RuleName("dogs only"),
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.9,
                            matched = true,
                        ),
                    promptScores = mapOf(),
                ),
            )
        val uploadRule =
            UploadRule(
                rule = RuleName("dogs only"),
                violationResponse = RuleViolationResponse("Dogs only!"),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset =
                    UploadRuleset(
                        default = DefaultRuleAction.REJECT,
                        acceptRules = listOf(uploadRule),
                    ),
                evaluationResult = RuleDefinitionsEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe true
        result.violationResponses shouldHaveSize 0
        result.labels.asMap() shouldBe AssetLabels.empty.asMap()
    }

    @Test
    fun `returns reject decision when rules match`() {
        val ruleDefinition =
            RuleDefinition(
                name = RuleName("dogs only"),
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.9,
                            matched = true,
                        ),
                    promptScores = mapOf(),
                ),
            )
        val uploadRule =
            UploadRule(
                rule = RuleName("dogs only"),
                violationResponse = RuleViolationResponse("Dogs only!"),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset = UploadRuleset(default = DefaultRuleAction.ACCEPT, rejectRules = listOf(uploadRule)),
                evaluationResult = RuleDefinitionsEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe false
        result.violationResponses shouldBe listOf(uploadRule.violationResponse)
        result.labels.asMap() shouldBe AssetLabels.empty.asMap()
    }

    @Test
    fun `returns accept decision when rules do not match and default is accept`() {
        val ruleDefinition =
            RuleDefinition(
                name = RuleName("dogs only"),
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.84,
                            matched = false,
                        ),
                    promptScores = mapOf(),
                ),
            )
        val uploadRule =
            UploadRule(
                rule = RuleName("dogs only"),
                violationResponse = RuleViolationResponse("Dogs only!"),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset = UploadRuleset(default = DefaultRuleAction.ACCEPT, rejectRules = listOf(uploadRule)),
                evaluationResult = RuleDefinitionsEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe true
        result.violationResponses shouldHaveSize 0
        result.labels.asMap() shouldBe AssetLabels.empty.asMap()
    }

    @Test
    fun `returns reject decision when rules do not match and default is reject`() {
        val ruleDefinition =
            RuleDefinition(
                name = RuleName("dogs only"),
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.84,
                            matched = false,
                        ),
                    promptScores = mapOf(),
                ),
            )
        val uploadRule =
            UploadRule(
                rule = RuleName("dogs only"),
                violationResponse = RuleViolationResponse("Dogs only!"),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset = UploadRuleset(default = DefaultRuleAction.REJECT, acceptRules = listOf(uploadRule)),
                evaluationResult = RuleDefinitionsEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe false
        result.violationResponses shouldHaveSize 0
        result.labels.asMap() shouldBe AssetLabels.empty.asMap()
    }

    @Test
    fun `returns label decision when rules match`() {
        val ruleDefinition =
            RuleDefinition(
                name = RuleName("dogs only"),
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.85,
                            matched = true,
                        ),
                    promptScores = mapOf(),
                ),
            )
        val uploadRule =
            UploadRule(
                rule = RuleName("dogs only"),
                labels = mapOf("phone" to "iphone").toAssetLabels(),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset = UploadRuleset(default = DefaultRuleAction.ACCEPT, labelRules = listOf(uploadRule)),
                evaluationResult = RuleDefinitionsEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe true
        result.violationResponses shouldHaveSize 0
        result.labels.asMap() shouldBe uploadRule.labels.asMap()
    }

    @Test
    fun `labels are merged in order when multiple rules match`() {
        val ruleDefinition1 =
            RuleDefinition(
                name = RuleName("dogs only"),
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val ruleDefinition2 =
            RuleDefinition(
                name = RuleName("cats only"),
                prompts = listOf("hello again"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition1,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.85,
                            matched = true,
                        ),
                    promptScores = mapOf(),
                ),
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition2,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.85,
                            matched = true,
                        ),
                    promptScores = mapOf(),
                ),
            )
        val uploadRule1 =
            UploadRule(
                rule = RuleName("dogs only"),
                labels = mapOf("phone" to "iphone", "car" to "compact").toAssetLabels(),
            )
        val uploadRule2 =
            UploadRule(
                rule = RuleName("cats only"),
                labels = mapOf("phone" to "android").toAssetLabels(),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset = UploadRuleset(default = DefaultRuleAction.ACCEPT, labelRules = listOf(uploadRule1, uploadRule2)),
                evaluationResult = RuleDefinitionsEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe true
        result.violationResponses shouldHaveSize 0
        result.labels.asMap() shouldBe mapOf("phone" to "android", "car" to "compact")
    }

    @Test
    fun `when label rule does not match then no labals are applied`() {
        val ruleDefinition =
            RuleDefinition(
                name = RuleName("dogs only"),
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.84,
                            matched = false,
                        ),
                    promptScores = mapOf(),
                ),
            )

        val uploadRule =
            UploadRule(
                rule = RuleName("dogs only"),
                labels = mapOf("phone" to "iphone").toAssetLabels(),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset = UploadRuleset(default = DefaultRuleAction.ACCEPT, labelRules = listOf(uploadRule)),
                evaluationResult = RuleDefinitionsEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe true
        result.violationResponses shouldHaveSize 0
        result.labels.asMap() shouldBe AssetLabels.empty.asMap()
    }

    @Test
    fun `can decision on both accept and label rules`() {
        val labelDefinition =
            RuleDefinition(
                name = RuleName("label rule"),
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val acceptDefinition =
            RuleDefinition(
                name = RuleName("accept rule"),
                prompts = listOf("hello again"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = labelDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.85,
                            matched = true,
                        ),
                    promptScores = mapOf(),
                ),
                RuleEvaluationResult(
                    ruleDefinition = acceptDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.85,
                            matched = true,
                        ),
                    promptScores = mapOf(),
                ),
            )
        val labelRule =
            UploadRule(
                rule = RuleName("label rule"),
                labels = mapOf("phone" to "iphone", "car" to "compact").toAssetLabels(),
            )
        val acceptRule =
            UploadRule(
                rule = RuleName("accept rule"),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset =
                    UploadRuleset(
                        default = DefaultRuleAction.REJECT,
                        labelRules = listOf(labelRule),
                        acceptRules = listOf(acceptRule),
                    ),
                evaluationResult = RuleDefinitionsEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe true
        result.violationResponses shouldHaveSize 0
        result.labels.asMap() shouldBe mapOf("phone" to "iphone", "car" to "compact")
    }

    @Test
    fun `labels are applied when reject rule fails`() {
        val labelDefinition =
            RuleDefinition(
                name = RuleName("label rule"),
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val rejectDefinition =
            RuleDefinition(
                name = RuleName("reject rule"),
                prompts = listOf("hello again"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = labelDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.85,
                            matched = true,
                        ),
                    promptScores = mapOf(),
                ),
                RuleEvaluationResult(
                    ruleDefinition = rejectDefinition,
                    evaluationScore =
                        EvaluationScore(
                            score = 0.80,
                            matched = false,
                        ),
                    promptScores = mapOf(),
                ),
            )
        val labelRule =
            UploadRule(
                rule = RuleName("label rule"),
                labels = mapOf("phone" to "iphone", "car" to "compact").toAssetLabels(),
            )
        val rejectRule =
            UploadRule(
                rule = RuleName("reject rule"),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset =
                    UploadRuleset(
                        default = DefaultRuleAction.ACCEPT,
                        labelRules = listOf(labelRule),
                        rejectRules = listOf(rejectRule),
                    ),
                evaluationResult = RuleDefinitionsEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe true
        result.violationResponses shouldHaveSize 0
        result.labels.asMap() shouldBe mapOf("phone" to "iphone", "car" to "compact")
    }
}
