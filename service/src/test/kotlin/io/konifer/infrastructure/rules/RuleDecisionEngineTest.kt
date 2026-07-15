package io.konifer.infrastructure.rules

import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.asset.toAssetLabels
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.domain.rules.RuleEvaluationResult
import io.konifer.domain.rules.RuleName
import io.konifer.domain.rules.RuleViolationResponse
import io.konifer.domain.rules.RulesetEvaluationResult
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
                definitionsByRule = emptyMap(),
                evaluationResult = RulesetEvaluationResult(results = emptyList()),
            )

        when (default) {
            DefaultRuleAction.REJECT -> {
                result.accept shouldBe false
                result.violationResponses shouldHaveSize 0
                result.labels.asMap() shouldBe AssetLabels.default.asMap()
            }
            DefaultRuleAction.ACCEPT -> {
                result.accept shouldBe true
                result.violationResponses shouldHaveSize 0
                result.labels.asMap() shouldBe AssetLabels.default.asMap()
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DefaultRuleAction::class)
    fun `returns default decision with matched labels when no acceptance rules are evaluated`(default: DefaultRuleAction) {
        val labelRules = listOf(
            UploadRule(
                rule = RuleName("phone"),
                labels = mapOf("phone" to "iphone").toAssetLabels(),
            )
        )
        val ruleDefinition =
            RuleDefinition(
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset = UploadRuleset(default = default, labelRules = labelRules),
                definitionsByRule = mapOf(labelRules.first() to ruleDefinition),
                evaluationResult = RulesetEvaluationResult(results = listOf(RuleEvaluationResult(ruleDefinition = ruleDefinition, score = 0.9, matched = true))),
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
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    score = 0.9,
                    matched = true,
                ),
            )
        val uploadRule =
            UploadRule(
                rule = RuleName("dogs only"),
                violationResponse = RuleViolationResponse("Dogs only!"),
            )
        val result =
            RuleDecisionEngine.makeDecision(
                uploadRuleset = UploadRuleset(default = DefaultRuleAction.REJECT, acceptRules = emptyList(), rejectRules = listOf(uploadRule)),
                definitionsByRule = mapOf(uploadRule to ruleDefinition),
                evaluationResult = RulesetEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe true
        result.violationResponses shouldHaveSize 0
    }

    @Test
    fun `returns reject decision when rules match`() {
        val ruleDefinition =
            RuleDefinition(
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    score = 0.9,
                    matched = true,
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
                definitionsByRule = mapOf(uploadRule to ruleDefinition),
                evaluationResult = RulesetEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe false
        result.violationResponses shouldBe listOf(uploadRule.violationResponse)
    }

    @Test
    fun `returns accept decision when rules do not match and default is accept`() {
        val ruleDefinition =
            RuleDefinition(
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    score = 0.84,
                    matched = false,
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
                definitionsByRule = mapOf(uploadRule to ruleDefinition),
                evaluationResult = RulesetEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe true
        result.violationResponses shouldHaveSize 0
    }

    @Test
    fun `returns reject decision when rules do not match and default is reject`() {
        val ruleDefinition =
            RuleDefinition(
                prompts = listOf("hello"),
                threshold = RuleDefinitionThreshold(0.85),
            )
        val evaluationResult =
            listOf(
                RuleEvaluationResult(
                    ruleDefinition = ruleDefinition,
                    score = 0.84,
                    matched = false,
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
                definitionsByRule = mapOf(uploadRule to ruleDefinition),
                evaluationResult = RulesetEvaluationResult(results = evaluationResult),
            )

        result.accept shouldBe false
        result.violationResponses shouldHaveSize 0
    }
}
