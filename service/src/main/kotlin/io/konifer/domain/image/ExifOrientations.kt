package io.konifer.domain.image

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

    /**
     * Normalize the rotation and flip from (clockwise [Rotate], [Flip]) to (clockwise [Rotate], [Boolean] horizontal flip)
     */
    fun normalizeOrientation(
        rotate: Rotate = Rotate.default,
        flip: Flip = Flip.default,
    ): Pair<Rotate, Boolean> =
        if (rotate == Rotate.ZERO && flip == Flip.NONE) {
            ONE
        } else if ((rotate == Rotate.ONE_HUNDRED_EIGHTY && flip == Flip.H) || (rotate == Rotate.ZERO && flip == Flip.V)) {
            TWO
        } else if (rotate == Rotate.ONE_HUNDRED_EIGHTY && flip == Flip.NONE) {
            THREE
        } else if ((rotate == Rotate.ZERO && flip == Flip.H) || (rotate == Rotate.ONE_HUNDRED_EIGHTY && flip == Flip.V)) {
            FOUR
        } else if ((rotate == Rotate.TWO_HUNDRED_SEVENTY && flip == Flip.H) || (rotate == Rotate.NINETY && flip == Flip.V)) {
            FIVE
        } else if (rotate == Rotate.TWO_HUNDRED_SEVENTY && flip == Flip.NONE) {
            SIX
        } else if ((rotate == Rotate.NINETY && flip == Flip.H) || (rotate == Rotate.TWO_HUNDRED_SEVENTY && flip == Flip.V)) {
            SEVEN
        } else if (rotate == Rotate.NINETY && flip == Flip.NONE) {
            EIGHT
        } else {
            throw IllegalArgumentException("Rotation not supported: $rotate, Flip: $flip")
        }
}
