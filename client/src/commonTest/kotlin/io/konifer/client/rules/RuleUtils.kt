package io.konifer.client.rules

import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.common.http.EvaluatedPromptResponse
import io.konifer.common.http.EvaluatedRuleDefinitionResponse

fun createEvaluateRulesResponse(): EvaluateRuleDefinitionsResponse =
    EvaluateRuleDefinitionsResponse(
        results =
            listOf(
                EvaluatedRuleDefinitionResponse(
                    name = "is-landscape",
                    threshold = 0.7,
                    score = 0.91,
                    matched = true,
                    promptScores =
                        listOf(
                            EvaluatedPromptResponse(
                                prompt = "image contains a desert landscape",
                                score = 0.91,
                            ),
                        ),
                ),
            ),
    )
