package io.konifer.domain.rules.upload

import io.konifer.common.serializer.LowercaseEnumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = DefaultRuleActionSerializer::class)
enum class DefaultRuleAction {
    ACCEPT,
    REJECT;

    companion object {
        val default = ACCEPT
    }
}

class DefaultRuleActionSerializer : KSerializer<DefaultRuleAction> by LowercaseEnumSerializer(DefaultRuleAction.entries)
