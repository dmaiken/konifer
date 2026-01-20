package io.konifer.infrastructure.path

import io.konifer.domain.path.PathConfiguration

data class PathTrieNode(
    val segment: String,
    var config: PathConfiguration,
    val children: MutableMap<String, PathTrieNode> = mutableMapOf(),
) {
    fun getOrCreateChild(
        segment: String,
        childPathConfiguration: PathConfiguration,
    ): PathTrieNode =
        children.getOrPut(segment) {
            PathTrieNode(segment, childPathConfiguration)
        }
}
