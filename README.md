<p align="center">
  <img src="https://konifer.io/img/konifer-small.png" alt="Konifer logo" width="100"/>
</p>

# Konifer

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/dmaiken/konifer/build.yml)
![Codecov](https://img.shields.io/codecov/c/github/dmaiken/konifer)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
![GitHub License](https://img.shields.io/github/license/dmaiken/konifer)
![Scanned with Trivy](https://img.shields.io/badge/scanned%20with-Trivy-1904DA?logo=trivy&logoColor=white)

Konifer is a self-hosted image backend for applications and vision pipelines. Upload an image once, then request a
WebP preview for your application or a resized raw pixel buffer for your inference worker. Define reusable
transformation profiles and manage storage, validation, and deletion through paths that match your application.

[10-minute quickstart](https://konifer.io/docs/start-here/getting-started) ·
[Pixels for inference](https://konifer.io/docs/guides/guides-pixels-for-inference) ·
[Documentation](https://konifer.io/) ·
[Performance report](https://dmaiken.github.io/konifer/performance/report/)

Konifer handles the image lifecycle. Put a CDN in front of it for delivery. If all you need is on-demand resizing,
[imgproxy](https://imgproxy.net/) or whatever your CDN offers may be a better fit.

## Why I built it

I've seen the same story on several teams: someone needs to store a product photo, then another feature needs
thumbnails, then a third needs different upload limits. Before long, image handling is spread across S3 buckets,
Lambdas, queues, and services. Konifer puts those jobs behind one API while letting each part of your application keep
its own rules.

It supports:

- Multipart uploads, S3 object ARN imports, and URL uploads with a domain allow-list enforced after redirects
- Image storage in S3-compatible buckets or a filesystem, coordinated with asset metadata
- Image transformations through libvips, including format conversion and animated GIF and WebP support
- Raw pixel output with configurable channel order and interleaved HWC or planar CHW layout
- Named transformation profiles with cached variants and optional eager generation
- JPEG, PNG, WebP, HEIC, AVIF, JPEG XL, and GIF
- Limits on image dimensions and pixel count, plus upload size limits
- In-process image classification with SigLIP2 to accept, reject, or label uploads
- Redirect-based image delivery when you want a CDN in front

Konifer is worth a look if you're adding images to an application, sharing preprocessing with vision workers, or
maintaining several image-handling services. You can run it yourself as a Docker image; there is no SaaS dependency.

## Serve display images and inference inputs

A product photo can serve a storefront and a classification worker. Define a profile for each consumer in
`konifer.conf`:

```hocon
variant-profiles {
  display {
    width = 256
    height = 256
    fit = fill
    rotate = auto
    format = webp
  }

  vision {
    width = 224
    height = 224
    fit = fill
    rotate = auto
    color-space = srgb
    format = pixels
    pixel-channels = RGB
    pixel-layout = planar
  }
}
```

Upload the source image, then request each representation:

```http
# Upload the source image
POST /assets/products/123/photo

# Fetch a display image
GET /assets/products/123/photo/-/entry/0/content?profile=display

# Fetch pixels for an inference worker
GET /assets/products/123/photo/-/entry/0/content?profile=vision
```

Choose dimensions, orientation, color space, channel order, and pixel layout in a named profile. Reuse the cached
variants across requests, or configure eager generation after upload.

This `vision` profile produces a `3 × 224 × 224` RGB buffer with one plane per channel. Your worker constructs the
tensor and applies the scaling and normalization required by your model.

See the [raw pixel contract](https://konifer.io/docs/concepts/concepts-pixels) for output details and the
[PyTorch inference guide](https://konifer.io/docs/guides/guides-pixels-for-inference) for a complete worker example,
including model-specific preprocessing considerations.

## Paths are part of the model

You can use a path that already means something to your application:

```http
POST /assets/users/123/profile-picture
GET  /assets/users/123/profile-picture/-/redirect?profile=thumbnail
GET  /assets/users/123/profile-picture/-/info
```

An opaque ID works too:

```http
POST /assets/0d79ddf9-8bbb-42a1-9435-9c166ca4dfb6
GET  /assets/0d79ddf9-8bbb-42a1-9435-9c166ca4dfb6/-/redirect?width=256&format=webp
```

Paths give you a way to work with related assets. If user 123 closes their account, you can delete the assets below
`users/123` with one request:

```http
POST   /assets/users/123/profile-picture
POST   /assets/users/123/article/456
POST   /assets/users/123/article/789
DELETE /assets/users/123/-/recursive
```

The same paths can choose how Konifer treats each image. This example gives every user image a pixel limit, creates a
thumbnail for profile pictures, and leaves article resizing to the CDN:

```hocon
variant-profiles {
  thumbnail {
    width = 256
    fit = fill
  }
}

paths {
  "/users/**" {
    limits {
      max-pixels = 15MP
    }
  }
  "/users/*/profile-picture" {
    object-store {
      bucket = profiles
    }
    transform {
      eager-variants = [thumbnail]
      on-demand-variant {
        mode = profile_only
      }
    }
  }
  "/users/*/article/**" {
    object-store {
      bucket = articles
    }
    allowed-formats = ["png", "jpg"]
    transform {
      on-demand-variant {
        mode = disabled
      }
    }
  }
}
```

Path rules inherit from broader matches, so both specific paths get the `/users/**` pixel limit. The
[path configuration docs](https://konifer.io/docs/concepts/concepts-path-configuration) cover matching and inheritance.

## Classify and validate uploads

Some uploads need a content check as well as a file type check. Konifer can compare an image with a collection of
prompts using SigLIP2 in the server process. For example:

```hocon
rule-definitions {
  "blood-and-gore" {
    prompts = [
      "graphic visible blood",
      "open wound with blood",
      "bloody injury scene",
      "gore and severe injury"
    ]
    threshold = 0.72
  }
}
```

You can attach a definition to a path's upload ruleset to accept, reject, or label matching images. The image stays on
your server during inference. Install the SigLIP2 model pack to use these rules; see
[upload rules](https://konifer.io/docs/concepts/concepts-upload-rules) for the full setup.

The [Rule Evaluation API](https://konifer.io/docs/concepts/concepts-rule-evaluation) lets you test prompts and
thresholds before enforcing a rule. Enable the API and send a URL or image bytes to `POST /rule-evaluations` without
storing an asset. The response includes rule matches and per-prompt scores. The scores depend on the image and model;
they are not confidence percentages. The API requires the same model pack.

## Documentation and performance

Start with the [quickstart](https://konifer.io/docs/start-here/getting-started), then see the
[asset model](https://konifer.io/docs/concepts/Assets/concepts-assets),
[image transformation reference](https://konifer.io/docs/reference/image-transformation-reference), and
[storage configuration](https://konifer.io/docs/reference/reference-variant-storage). The [full documentation](https://konifer.io/)
also covers caching, URL signing, and deployment.

The [interactive performance report](https://dmaiken.github.io/konifer/performance/report/) tracks latency across
releases and mixed workloads. Its current runs use a laptop; AWS hardware testing is planned.

## Development

Konifer ships as a Docker image. For local development, install the native dependencies and download the SigLIP2
model pack if you work on inference or its tests. [CONTRIBUTING.md](CONTRIBUTING.md) has the setup steps, Gradle tasks,
and macOS notes.

## Acknowledgments

A huge thank-you to these open-source projects:

- **[libvips](https://github.com/libvips/libvips)**: The image processor behind Konifer's transformations.
- **[vips-ffm](https://github.com/lopcode/vips-ffm)**: The Java FFM bindings Konifer uses to call libvips.
- **[jOOQ](https://github.com/jooq/jooq)**: Database access and generated query code.
- **[Ktor](https://github.com/ktorio/ktor)**: The Kotlin server framework.
- **[ONNX Runtime](https://onnxruntime.ai/)**: In-process model inference.

## Contact me

Questions or feedback? Email me at [daniel@konifer.io](mailto:daniel@konifer.io) or start a discussion on GitHub.

## License

The Konifer server is released under the AGPL license in [LICENSE](LICENSE).

The Konifer client and common module are licensed under Apache 2.0 license in [LICENSE](client/LICENSE) and
[LICENSE](common/LICENSE), respectively.
