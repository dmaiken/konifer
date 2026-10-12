package io.konifer.common.image.pixel

import io.konifer.common.image.Manipulation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object PixelTypeParameterValues {
    const val UINT8 = "uint8"
    const val INT8 = "int8"
    const val UINT16 = "uint16"
    const val INT16 = "int16"
    const val UINT32 = "uint32"
    const val INT32 = "int32"
    const val FLOAT32 = "float32"
    const val FLOAT64 = "float64"
    const val COMPLEX64 = "complex64"
    const val COMPLEX128 = "complex128"
}

@Serializable
enum class PixelDataType(
    override val queryParameterValue: String,
) : Manipulation {
    /**
     * Unsigned integer, 0–255
     */
    @SerialName(PixelTypeParameterValues.UINT8)
    UINT8(PixelTypeParameterValues.UINT8),

    /**
     * Signed integer, −128–127
     */
    @SerialName(PixelTypeParameterValues.INT8)
    INT8(PixelTypeParameterValues.INT8),

    /**
     * Unsigned integer, 0–65,535
     */
    @SerialName(PixelTypeParameterValues.UINT16)
    UINT16(PixelTypeParameterValues.UINT16),

    /**
     * Signed integer, −32,768–32,767
     */
    @SerialName(PixelTypeParameterValues.INT16)
    INT16(PixelTypeParameterValues.INT16),

    /**
     * Unsigned integer
     */
    @SerialName(PixelTypeParameterValues.UINT32)
    UINT32(PixelTypeParameterValues.UINT32),

    /**
     * Signed integer
     */
    @SerialName(PixelTypeParameterValues.INT32)
    INT32(PixelTypeParameterValues.INT32),

    /**
     * Single-precision floating point
     */
    @SerialName(PixelTypeParameterValues.FLOAT32)
    FLOAT32(PixelTypeParameterValues.FLOAT32),

    /**
     * Double-precision floating point
     */
    @SerialName(PixelTypeParameterValues.FLOAT64)
    FLOAT64(PixelTypeParameterValues.FLOAT64),

    /**
     * Two `float32` values: real + imaginary
     */
    @SerialName(PixelTypeParameterValues.COMPLEX64)
    COMPLEX64(PixelTypeParameterValues.COMPLEX64),

    /**
     * Two `float64` values: real + imaginary
     */
    @SerialName(PixelTypeParameterValues.COMPLEX128)
    COMPLEX128(PixelTypeParameterValues.COMPLEX128),
}
