package io.direkt.service.transformation

object ColorConverter {
    private const val MAX_ALPHA = "FF"
    private val hexColorRegex = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    val transparent = listOf(0, 0, 0, 0)
    val white = listOf(255, 255, 255, 255)

    fun toRgba(hex: String): List<Int> {
        if (!hexColorRegex.matches(hex)) {
            throw IllegalArgumentException("Invalid hex string: $hex")
        }

        val cleanHex = hex.removePrefix("#")

        val fullHex =
            when (cleanHex.length) {
                // #RGB → #RRGGBB + alpha=255
                3 -> cleanHex.map { "$it$it" }.joinToString("") + MAX_ALPHA
                // #RGBA → #RRGGBBAA
                4 -> cleanHex.map { "$it$it" }.joinToString("")
                // #RRGGBB → add alpha=255
                6 -> cleanHex + MAX_ALPHA
                // #RRGGBBAA
                8 -> cleanHex
                else -> throw IllegalArgumentException("Invalid hex color: $hex")
            }

        val r = fullHex.take(2).toInt(16)
        val g = fullHex.substring(2, 4).toInt(16)
        val b = fullHex.substring(4, 6).toInt(16)
        val a = fullHex.substring(6, 8).toInt(16)

        return listOf(r, g, b, a)
    }
}