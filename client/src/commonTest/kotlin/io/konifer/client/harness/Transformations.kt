package io.konifer.client.harness

import io.konifer.client.RequestedTransformation
import io.konifer.client.requestedTransformation
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.common.image.TransformableColorSpace

val allTransformationsDsl =
    requestedTransformation {
        height = 10
        width = 5
        fit = Fit.FIT
        filter = Filter.BLACK_WHITE
        flip = Flip.H
        blur = 100
        gravity = Gravity.CENTER
        format = ImageFormat.GIF
        rotate = Rotate.NINETY
        quality = 55
        pad = 25
        padColor = "#123456"
        profile = "profile"
        strip(MetadataType.EXIF, MetadataType.XMP, MetadataType.IPTC)
        colorSpace = TransformableColorSpace.P3
    }

val allTransformationsBuilder =
    RequestedTransformation
        .Builder()
        .height(10)
        .width(5)
        .fit(Fit.FIT)
        .filter(Filter.BLACK_WHITE)
        .flip(Flip.H)
        .blur(100)
        .gravity(Gravity.CENTER)
        .format(ImageFormat.GIF)
        .rotate(Rotate.NINETY)
        .quality(55)
        .pad(25)
        .padColor("#123456")
        .profile("profile")
        .strip(MetadataType.EXIF, MetadataType.XMP, MetadataType.IPTC)
        .colorSpace(TransformableColorSpace.P3)
        .build()
