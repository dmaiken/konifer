package io.image.model

/**
 * EXIF orientation mappings
 */
object ExifOrientations {
    val ONE = Pair(Rotate.ZERO, false)
    val TWO = Pair(Rotate.ONE_HUNDRED_EIGHTY, true)
    val THREE = Pair(Rotate.ONE_HUNDRED_EIGHTY, false)
    val FOUR = Pair(Rotate.ZERO, true)
    val FIVE = Pair(Rotate.TWO_HUNDRED_SEVENTY, true)
    val SIX = Pair(Rotate.TWO_HUNDRED_SEVENTY, false)
    val SEVEN = Pair(Rotate.NINETY, true)
    val EIGHT = Pair(Rotate.NINETY, false)
}