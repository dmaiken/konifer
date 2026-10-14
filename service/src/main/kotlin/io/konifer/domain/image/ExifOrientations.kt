package io.konifer.domain.image

import io.konifer.common.image.Flip
import io.konifer.common.image.Rotate

/**
 * EXIF orientation mapped to clockwise rotation followed by a horizontal flip.
 */
object ExifOrientations {
    val ONE = Pair(Rotate.ZERO, false)
    val TWO = Pair(Rotate.ZERO, true)
    val THREE = Pair(Rotate.ONE_HUNDRED_EIGHTY, false)
    val FOUR = Pair(Rotate.ONE_HUNDRED_EIGHTY, true)
    val FIVE = Pair(Rotate.NINETY, true)
    val SIX = Pair(Rotate.NINETY, false)
    val SEVEN = Pair(Rotate.TWO_HUNDRED_SEVENTY, true)
    val EIGHT = Pair(Rotate.TWO_HUNDRED_SEVENTY, false)

    fun fromExifOrientation(orientation: Int): Pair<Rotate, Boolean> =
        when (orientation) {
            1 -> ONE
            2 -> TWO
            3 -> THREE
            4 -> FOUR
            5 -> FIVE
            6 -> SIX
            7 -> SEVEN
            8 -> EIGHT
            else -> throw IllegalArgumentException("Invalid orientation")
        }

    /**
     * Normalize the rotation and flip from (clockwise [Rotate], [io.konifer.common.image.Flip]) to (clockwise [Rotate], [Boolean] horizontal flip)
     */
    fun normalizeOrientation(
        rotate: Rotate = Rotate.default,
        flip: Flip = Flip.default,
    ): Pair<Rotate, Boolean> =
        if (rotate == Rotate.ZERO && flip == Flip.NONE) {
            ONE
        } else if ((rotate == Rotate.ONE_HUNDRED_EIGHTY && flip == Flip.H) || (rotate == Rotate.ZERO && flip == Flip.V)) {
            FOUR
        } else if (rotate == Rotate.ONE_HUNDRED_EIGHTY && flip == Flip.NONE) {
            THREE
        } else if ((rotate == Rotate.ZERO && flip == Flip.H) || (rotate == Rotate.ONE_HUNDRED_EIGHTY && flip == Flip.V)) {
            TWO
        } else if ((rotate == Rotate.TWO_HUNDRED_SEVENTY && flip == Flip.H) || (rotate == Rotate.NINETY && flip == Flip.V)) {
            SEVEN
        } else if (rotate == Rotate.TWO_HUNDRED_SEVENTY && flip == Flip.NONE) {
            EIGHT
        } else if ((rotate == Rotate.NINETY && flip == Flip.H) || (rotate == Rotate.TWO_HUNDRED_SEVENTY && flip == Flip.V)) {
            FIVE
        } else if (rotate == Rotate.NINETY && flip == Flip.NONE) {
            SIX
        } else {
            throw IllegalArgumentException("Rotation not supported: $rotate, Flip: $flip")
        }
}
