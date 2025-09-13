package io.image.vips

/**
 * Compute the integer shrink factor for libvips shrink-on-load.
 * Shrink is always a power-of-two: 1, 2, 4, 8...
 */
fun computeShrink(
    origWidth: Int,
    origHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): Int {
    val hScale = targetWidth.toDouble() / origWidth
    val vScale = targetHeight.toDouble() / origHeight
    val scale = minOf(hScale, vScale)

    var shrink = 1
    val temp = 1.0 / scale
    while (shrink * 2 <= temp) {
        shrink *= 2
    }
    return shrink
}

/**
 * Compute the remaining reduce factor to reach exact target dimensions.
 * This is applied after shrink-on-load.
 */
fun computeReduce(
    origWidth: Int,
    origHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    shrink: Int,
): Double {
    val intermediateWidth = origWidth / shrink.toDouble()
    val intermediateHeight = origHeight / shrink.toDouble()

    val hReduce = targetWidth.toDouble() / intermediateWidth
    val vReduce = targetHeight.toDouble() / intermediateHeight

    // Use separate horizontal/vertical reduce if needed; or choose one for uniform scaling
    return minOf(hReduce, vReduce)
}
