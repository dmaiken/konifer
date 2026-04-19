package io.konifer.client

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class RequestedTransformationTest :
    FunSpec({

        test("builder produces the same output as DSL does") {
            val createdByDsl =
                requestedTransformation {
                    height(10)
                    width(5)
                    fit(Fit.FIT)
                    filter(Filter.BLACK_WHITE)
                    flip(Flip.H)
                    blur(100)
                    gravity(Gravity.CENTER)
                    format(ImageFormat.GIF)
                    rotate(Rotate.NINETY)
                    quality(55)
                    pad(25)
                    padColor("#123456")
                    profile("profile")
                }

            val createdByBuilder =
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
                    .build()

            createdByDsl.height shouldNotBe null shouldBe createdByBuilder.height
            createdByDsl.width shouldNotBe null shouldBe createdByBuilder.width
            createdByDsl.fit shouldNotBe null shouldBe createdByBuilder.fit
            createdByDsl.filter shouldNotBe null shouldBe createdByBuilder.filter
            createdByDsl.flip shouldNotBe null shouldBe createdByBuilder.flip
            createdByDsl.blur shouldNotBe null shouldBe createdByBuilder.blur
            createdByDsl.gravity shouldNotBe null shouldBe createdByBuilder.gravity
            createdByDsl.format shouldNotBe null shouldBe createdByBuilder.format
            createdByDsl.rotate shouldNotBe null shouldBe createdByBuilder.rotate
            createdByDsl.quality shouldNotBe null shouldBe createdByBuilder.quality
            createdByDsl.pad shouldNotBe null shouldBe createdByBuilder.pad
            createdByDsl.padColor shouldNotBe null shouldBe createdByBuilder.padColor
            createdByDsl.profile shouldNotBe null shouldBe createdByBuilder.profile
        }
    })
