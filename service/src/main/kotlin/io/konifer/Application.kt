package io.konifer

import io.konifer.domain.asset.MAX_BYTES_DEFAULT
import io.konifer.entrypoint.configureAssetRouting
import io.konifer.entrypoint.configureHealthRouting
import io.konifer.entrypoint.configureInMemoryObjectStoreRouting
import io.konifer.entrypoint.configureRuleEvaluationRouting
import io.konifer.infrastructure.configureKoin
import io.konifer.infrastructure.getObjectStoreProvider
import io.konifer.infrastructure.http.cache.configureConditionalHeaders
import io.konifer.infrastructure.http.configureCompression
import io.konifer.infrastructure.http.exception.configureStatusPages
import io.konifer.infrastructure.http.serialization.configureContentNegotiation
import io.konifer.infrastructure.http.signature.configureSignatureVerification
import io.konifer.infrastructure.objectstore.ObjectStoreProvider
import io.konifer.infrastructure.path.extractRawHocon
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ApiPropertyKeys
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ApiPropertyKeys.RuleEvaluationPropertyKeys
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SOURCE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SourceConfigurationPropertyKeys.MULTIPART
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SourceConfigurationPropertyKeys.MultipartConfigurationPropertyKeys.MAX_BYTES
import io.konifer.infrastructure.rules.getRuleDefinitions
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.server.netty.EngineMain
import io.ktor.util.logging.KtorSimpleLogger
import org.koin.core.module.Module

private val logger = KtorSimpleLogger("io.konifer.Application")

/**
 * Before you think about configuring this using embeddedServer, think again. This will break the ability to use
 * eternalized config through ktor (-config) and you will have to mount any externalized config manually!
 */
fun main(args: Array<String>) {
    logger.info("Starting Konifer...")
    EngineMain.main(args)
}

fun Application.module() {
    serviceModule(additionalModules = emptyList())
}

fun Application.serviceModule(additionalModules: List<Module>) {
    val objectStoreProvider = environment.config.getObjectStoreProvider()
    val ruleDefinitions = environment.config.extractRawHocon().getRuleDefinitions()
    val shouldEnableEvaluationApi =
        environment.config
            .tryGetConfig(ConfigurationPropertyKeys.API)
            ?.tryGetConfig(ApiPropertyKeys.RULE_EVALUATION)
            ?.tryGetString(RuleEvaluationPropertyKeys.ENABLED)
            ?.toBoolean() ?: false

    configureKoin(
        objectStoreProvider = objectStoreProvider,
        ruleDefinitions = ruleDefinitions,
        shouldEnableEvaluationApi = shouldEnableEvaluationApi,
        additionalModules = additionalModules,
    )
    configureContentNegotiation()
    configureRouting(
        objectStoreProvider = objectStoreProvider,
        shouldEnableRuleEvaluationsRoutes = shouldEnableEvaluationApi,
    )
    configureStatusPages()
    configureSignatureVerification()
    configureConditionalHeaders()
    configureCompression()
}

fun Application.configureRouting(
    objectStoreProvider: ObjectStoreProvider,
    shouldEnableRuleEvaluationsRoutes: Boolean,
) {
    configureHealthRouting()

    val maxMultipartContentLength =
        environment.config
            .tryGetConfig(SOURCE)
            ?.tryGetConfig(MULTIPART)
            ?.tryGetString(MAX_BYTES)
            ?.toLong()
            ?: MAX_BYTES_DEFAULT

    configureAssetRouting(maxMultipartContentLength)
    if (shouldEnableRuleEvaluationsRoutes) {
        configureRuleEvaluationRouting(maxMultipartContentLength)
    }
    if (objectStoreProvider == ObjectStoreProvider.IN_MEMORY) {
        configureInMemoryObjectStoreRouting()
    }
}
