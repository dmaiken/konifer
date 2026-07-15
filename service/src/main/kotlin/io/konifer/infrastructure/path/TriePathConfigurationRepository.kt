package io.konifer.infrastructure.path

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.PathConfigurationRepository
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig

class TriePathConfigurationRepository(
    rawConfig: Config,
) : PathConfigurationRepository {
    companion object {
        private const val WILDCARD_SEGMENT = "*"
        private const val GREEDY_WILDCARD_SEGMENT = "**"
        private const val DEFAULT_PATH = "/$GREEDY_WILDCARD_SEGMENT"
        private const val GREEDY_WILDCARD_SPECIFICITY = "0"
        private const val WILDCARD_SPECIFICITY = "1"
        private const val EXACT_SPECIFICITY = "2"
    }

    private val root = initializeTrieWithDefault()
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        constructPathConfigurationTrie(rawConfig)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun fetch(path: String): PathConfiguration {
        val segments =
            path
                .trim('/')
                .lowercase()
                .split('/')
                .filter { it.isNotBlank() }

        val matches = matchRecursive(root, segments)
        if (matches.isEmpty()) {
            return PathConfiguration.default
        }

        val mergedRawConfig =
            matches
                .sortedWith(compareBy<MatchResult> { it.depth }.thenBy { it.specificity })
                .fold(ConfigFactory.empty()) { merged, match ->
                    match.node.rawConfig.withFallback(merged)
                }

        return Hocon.decodeFromConfig<PathConfiguration>(mergedRawConfig)
    }

    private fun initializeTrieWithDefault(): PathTrieNode =
        PathTrieNode(
            segment = "", // The root conceptually represents the base '/'
            rawConfig = ConfigFactory.empty(),
            parsedConfig = PathConfiguration.default,
        )

    @OptIn(ExperimentalSerializationApi::class)
    private fun constructPathConfigurationTrie(config: Config) {
        if (!config.hasPath(ConfigurationPropertyKeys.PATH_CONFIGURATION)) {
            logger.info("No explicit paths configuration found, using defaults.")
            return
        }

        val pathsConfig = config.getConfig(ConfigurationPropertyKeys.PATH_CONFIGURATION)
        val pathsMap = pathsConfig.root()

        // Handle the root/default path first so everything else can inherit from it
        val rootValue = pathsMap[DEFAULT_PATH]
        if (rootValue != null) {
            val rootNodeConfig =
                (rootValue as? ConfigObject)?.toConfig()
                    ?: throw IllegalArgumentException("Configuration for $DEFAULT_PATH must be an object")

            root.rawConfig = rootNodeConfig
            root.parsedConfig = Hocon.decodeFromConfig<PathConfiguration>(rootNodeConfig)
            root.hasExplicitConfiguration = true
            logger.info("Applied base configuration to root node from $DEFAULT_PATH")
        }

        // Iterate over the remaining map entries
        pathsMap.forEach { (pathKey, configValue) ->
            if (pathKey.isBlank()) {
                throw IllegalArgumentException("Path key cannot be blank")
            }
            if (pathKey != DEFAULT_PATH) {
                val nodeConfig =
                    (configValue as? ConfigObject)?.toConfig()
                        ?: throw IllegalArgumentException("Configuration for path '$pathKey' must be an object")

                logger.info("Parsing config for specific path: $pathKey")
                insertPath(
                    path = pathKey,
                    nodeConfig = nodeConfig,
                )
            }
        }
        logger.info("Populated config trie: $root")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun insertPath(
        path: String,
        nodeConfig: Config,
    ) {
        val segments =
            path
                .trim('/')
                .lowercase()
                .split("/")
                .filter { it.isNotBlank() }

        var current = root

        segments.forEach { segment ->
            current = current.getOrCreateChild(segment, current.rawConfig, current.parsedConfig)
        }

        // Merge the explicit configuration with the inherited configuration
        val mergedRawConfig = nodeConfig.withFallback(current.rawConfig)

        current.rawConfig = mergedRawConfig
        current.parsedConfig = Hocon.decodeFromConfig<PathConfiguration>(mergedRawConfig)
        current.hasExplicitConfiguration = true
    }

    private fun matchRecursive(
        node: PathTrieNode,
        segments: List<String>,
        depth: Int = 0,
        specificity: String = "",
    ): List<MatchResult> {
        val candidates = mutableListOf<MatchResult>()
        if (node === root && node.hasExplicitConfiguration) {
            candidates += MatchResult(node, depth, specificity + GREEDY_WILDCARD_SPECIFICITY)
        }

        if (segments.isEmpty()) {
            if (node.hasExplicitConfiguration) {
                return listOf(MatchResult(node, depth, specificity))
            }

            node.children[WILDCARD_SEGMENT]?.let { wildcard ->
                candidates += matchRecursive(wildcard, emptyList(), depth + 1, specificity + WILDCARD_SPECIFICITY)
            }

            node.children[GREEDY_WILDCARD_SEGMENT]?.let { greedy ->
                candidates += matchRecursive(greedy, emptyList(), depth + 1, specificity + GREEDY_WILDCARD_SPECIFICITY)
            }

            return candidates
        }

        val segment = segments.first()
        val remaining = segments.drop(1)

        node.children[segment]?.let { exact ->
            candidates += matchRecursive(exact, remaining, depth + 1, specificity + EXACT_SPECIFICITY)
        }

        node.children[WILDCARD_SEGMENT]?.let { wildcard ->
            candidates += matchRecursive(wildcard, remaining, depth + 1, specificity + WILDCARD_SPECIFICITY)
        }

        node.children[GREEDY_WILDCARD_SEGMENT]?.let { greedy ->
            // Allow ** to consume 0..N segments
            for (i in 0..segments.size) {
                val tail = segments.drop(i)
                candidates += matchRecursive(greedy, tail, depth + 1, specificity + GREEDY_WILDCARD_SPECIFICITY)
                if (tail.isEmpty()) break // already matched all segments
            }
        }

        return candidates
    }

    private data class MatchResult(
        val node: PathTrieNode,
        val depth: Int,
        val specificity: String,
    )
}
