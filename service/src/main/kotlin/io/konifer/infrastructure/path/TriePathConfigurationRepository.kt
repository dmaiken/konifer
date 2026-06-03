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
    }

    private val root = initializeTrieWithDefault()
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        constructPathConfigurationTrie(rawConfig)
    }

    override fun fetch(path: String): PathConfiguration {
        val segments =
            path
                .trim('/')
                .lowercase()
                .split('/')
                .filter { it.isNotBlank() }

        return matchRecursive(root, segments).node.parsedConfig
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
    ): MatchResult {
        // Base case
        if (segments.isEmpty()) {
            if (node.hasExplicitConfiguration) {
                return MatchResult(node, depth)
            }

            val candidates = mutableListOf<MatchResult>()

            node.children[WILDCARD_SEGMENT]?.let { wildcard ->
                candidates += matchRecursive(wildcard, emptyList(), depth + 1)
            }

            node.children[GREEDY_WILDCARD_SEGMENT]?.let { greedy ->
                candidates += matchRecursive(greedy, emptyList(), depth + 1)
            }

            candidates.maxByOrNull { it.depth }?.let { return it }
            return MatchResult(node, depth)
        }

        val segment = segments.first()
        val remaining = segments.drop(1)
        val candidates = mutableListOf<MatchResult>()

        node.children[segment]?.let { exact ->
            candidates += matchRecursive(exact, remaining, depth + 1)
        }

        node.children[WILDCARD_SEGMENT]?.let { wildcard ->
            candidates += matchRecursive(wildcard, remaining, depth + 1)
        }

        node.children[GREEDY_WILDCARD_SEGMENT]?.let { greedy ->
            // Allow ** to consume 0..N segments
            for (i in 0..segments.size) {
                val tail = segments.drop(i)
                val result = matchRecursive(greedy, tail, depth + 1)
                candidates += result
                if (tail.isEmpty()) break // already matched all segments
            }
        }

        // No match
        return candidates.maxByOrNull { it.depth } ?: MatchResult(node, depth)
    }

    private data class MatchResult(
        val node: PathTrieNode,
        val depth: Int,
    )
}
