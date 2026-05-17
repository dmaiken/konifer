package io.konifer.client

import io.konifer.client.harness.allTransformationsBuilder
import io.konifer.client.harness.allTransformationsDsl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class RequestedTransformationTest :
    FunSpec({

        test("builder produces the same output as DSL does") {
            val createdByDsl = allTransformationsDsl
            val createdByBuilder = allTransformationsBuilder

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
            createdByDsl.strip shouldNotBe null shouldBe createdByBuilder.strip
            createdByDsl.colorSpace shouldNotBe null shouldBe createdByBuilder.colorSpace
        }
    })
