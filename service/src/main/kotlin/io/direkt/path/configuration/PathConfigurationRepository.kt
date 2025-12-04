package io.direkt.path.configuration

import io.direkt.properties.ConfigurationProperties
import io.direkt.properties.ConfigurationProperties.PathConfigurationProperties.PATH
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.ktor.util.logging.KtorSimpleLogger

class PathConfigurationRepository(
    applicationConfig: ApplicationConfig,
) {
    companion object {
        private const val WILDCARD_SEGMENT = "*"
        private const val GREEDY_WILDCARD_SEGMENT = "**"
        private const val DEFAULT_PATH = "/$GREEDY_WILDCARD_SEGMENT"
    }

    private val root = initializeTrieWithDefault(applicationConfig)
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        constructPathConfigurationTrie(applicationConfig)
    }

    fun fetch(path: String): PathConfiguration {
        val segments =
            path
                .trim('/')
                .lowercase()
                .split('/')
                .filter { it.isNotBlank() }

        return matchRecursive(root, segments).node.config
    }

    private fun initializeTrieWithDefault(applicationConfig: ApplicationConfig): PathTrieNode {
        val defaultConfig =
            applicationConfig
                .configList(ConfigurationProperties.PATH_CONFIGURATION)
                .firstOrNull { it.tryGetString(PATH) == DEFAULT_PATH }
        return PathTrieNode(
            segment = GREEDY_WILDCARD_SEGMENT,
            config = PathConfiguration.create(defaultConfig),
        )
    }

    private fun constructPathConfigurationTrie(applicationConfig: ApplicationConfig) {
        applicationConfig
            .configList(ConfigurationProperties.PATH_CONFIGURATION)
            .filter { it.tryGetString(PATH) != DEFAULT_PATH } // skip the default that we already populated
            .forEach { pathConfig ->
                insertPath(
                    path =
                        pathConfig.tryGetString(PATH)?.trim()
                            ?: throw IllegalArgumentException("Path configuration must be supplied"),
                    applicationConfig = pathConfig,
                )
            }
        logger.info("Populated config trie: {}", root)
    }

    private fun insertPath(
        path: String,
        applicationConfig: ApplicationConfig,
    ) {
        val segments =
            path
                .trim('/')
                .lowercase()
                .split("/")
                .filter { it.isNotBlank() }
        var current = root
        segments.forEach { segment ->
            current = current.getOrCreateChild(segment, current.config)
        }
        current.config = PathConfiguration.create(applicationConfig, current.config)
    }

    private fun matchRecursive(
        node: PathTrieNode,
        segments: List<String>,
        depth: Int = 0,
    ): MatchResult {
        // Base case
        if (segments.isEmpty()) {
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
