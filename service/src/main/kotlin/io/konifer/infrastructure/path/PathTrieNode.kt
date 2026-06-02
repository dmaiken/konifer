package io.konifer.infrastructure.path

import com.typesafe.config.Config
import io.konifer.domain.path.PathConfiguration

data class PathTrieNode(
    val segment: String,
    var rawConfig: Config,
    var parsedConfig: PathConfiguration,
    var hasExplicitConfiguration: Boolean = false,
    val children: MutableMap<String, PathTrieNode> = mutableMapOf(),
) {
    fun getOrCreateChild(
        segment: String,
        parentRawConfig: Config,
        parentParsedConfig: PathConfiguration,
    ): PathTrieNode =
        children.getOrPut(segment) {
            PathTrieNode(
                segment = segment,
                rawConfig = parentRawConfig,
                parsedConfig = parentParsedConfig,
            )
        }
}
