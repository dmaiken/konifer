package io.konifer.service.variant

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.common.image.ImageFormat

object ObjectStoreKeyFactory {
    fun newKey(format: ImageFormat) = "${UuidCreator.getRandomBasedFast()}${format.extension}"
}
