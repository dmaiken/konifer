package io.path.configuration

data class PathTrieNode(
    val segment: String,
    var config: PathConfiguration,
    val children: MutableMap<String, PathTrieNode> = mutableMapOf(),
) {
    fun getOrCreateChild(
        segment: String,
        childPathConfiguration: PathConfiguration,
    ): PathTrieNode {
        return children.getOrPut(segment) {
            PathTrieNode(segment, childPathConfiguration)
        }
    }
}
