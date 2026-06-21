package io.konifer.domain.rules

import java.nio.file.Path

@JvmInline
value class RuleEvaluationInput(
    val path: Path,
)
