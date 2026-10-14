package io.konifer.common.http

/** Headers describing the asset and selected variant on content/download responses. */
object AssetContentHeaders {
    const val ALT = "K-Alt"
    const val BLURHASH = "K-LQIP-Blurhash"
    const val THUMBHASH = "K-LQIP-Thumbhash"
    const val WIDTH = "K-Attr-Width"
    const val HEIGHT = "K-Attr-Height"
    const val FORMAT = "K-Attr-Format"
    const val ROTATE = "K-Attr-Rotate"
    const val HORIZONTAL_FLIP = "K-Attr-Horizontal-Flip"
    const val COLOR_SPACE = "K-Attr-Color-Space"
    const val PAGE_COUNT = "K-Attr-Page-Count"
    const val LOOP = "K-Attr-Loop"
    const val PIXELS_CHANNELS = "K-Attr-Pixels-Channels"
    const val PIXELS_LAYOUT = "K-Attr-Pixels-Layout"
    const val PIXELS_DATA_TYPE = "K-Attr-Pixels-Data-Type"

    val attributeNames: List<String> =
        listOf(
            WIDTH,
            HEIGHT,
            FORMAT,
            ROTATE,
            HORIZONTAL_FLIP,
            COLOR_SPACE,
            PAGE_COUNT,
            LOOP,
            PIXELS_CHANNELS,
            PIXELS_LAYOUT,
            PIXELS_DATA_TYPE,
        )
}
