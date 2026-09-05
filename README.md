<p align="center">
  <img src="https://konifer.io/img/konifer-small.png" alt="Konifer logo" width="100"/>
</p>

# Konifer

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/dmaiken/konifer/build.yml)
![Codecov](https://img.shields.io/codecov/c/github/dmaiken/konifer)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)](http://kotlinlang.org)
![GitHub License](https://img.shields.io/github/license/dmaiken/konifer)

Konifer is a backend for managing application-owned media. It manages the ingestion, storage, and lifecycle of images
your application has to deal with. This can be profile pictures, avatars, listing photos, card art, anything that 
your application has to take in, validate, transform, and otherwise manage. 

Konifer is not a CDN. Konifer is not [imgproxy](https://imgproxy.net/). While Konifer can support image delivery, your
CDN is probably better positioned to handle this.

Konifer goes to great lengths to support your existing domain model. It's 
[Assets API](https://konifer.io/docs/concepts/Assets/concepts-assets) is flexible and hierarchical by 
design. It binds a path-based URL structure with configuration you associate to that structure because your profile
pictures need to be treated separately from photos added to a blog post. However your domain is modeled, Konifer does
not care. It's your sandbox, Konifer merely brings the buckets and shovels.

## Path structure

Let the path define what your image is, and avoid having to store an `imageId`.

```http
POST /assets/users/123/profile-picture
GET  /assets/users/123/profile-picture/-/redirect?profile=thumbnail
GET  /assets/users/123/profile-picture/-/info
```

If you prefer an `imageId`, use them.

```http
POST /assets/0d79ddf9-8bbb-42a1-9435-9c166ca4dfb6
GET  /assets/0d79ddf9-8bbb-42a1-9435-9c166ca4dfb6/-/redirect?w=256&format=webp
```

Let's say your user has several images. Konifer lets you manage it seamlessly.

```http
POST /assets/users/123/profile-picture
POST /assets/users/123/article/456
POST /assets/users/123/article/789

# User 123 closed their account
# Delete users/123 and everything below it atomically
DELETE /assets/users/123/-/recursive
```

Want your article assets treated differently than your profile pictures? Use the Path Configuration.

```hocon
# Custom transformations
variant-profiles {
  thumbnail {
    w = 256
    fit = fill
  }
}
paths {
  # Configuration is inherited with most-specific path's configuration winning
  "/users/**" {
    limits {
      max-bytes = 20MB
      max-pixels = 15MP # Reject uploads larger than 15 mega-pixels
    }
    transform {
      preprocessing {
        r = auto # Auto-rotate the image
        enabled = true
        clamp-width = 2048
        clamp-height = 2048
        fit = fit # fit inside a 2048x2048 bounding box
      }
    }
  }
  "/users/*/profile-pictures" {
    transform {
      object-store {
        bucket = profiles
      }
      eager-variants = [thumbnail]
      # Only allow transformations defined in variant-profiles
      on-demand-variant {
        mode = profile_only
      }
    }
  }
  "/users/*/article/**" {
    object-store {
      bucket = articles
    }
    # Disable Konifer on-demand variants and let your CDN handle the resizing
    on-demand-variant {
      mode = disabled
    }
    allowed-content-types = [ "image/png", "image/jpeg" ]
  }
}
```

## Why Konifer exists

Konifer was built to solve a problem I have seen on several teams throughout my career. What started as a simple
requirement to store a product photo has grown to a patchwork of S3 buckets, lambdas, queueing, and microservices. 
You may even have different architectures for different types of images.

Konifer exists to unify all of this. It brings to the table:

- Multi-part uploads
- URL uploads with a domain allow-list (which is enforced on URL-redirects)
- Hardened state management between your S3-compatible object store (or filesystem) and it's metadata store
- Efficient transformation of images powered by libvips
- Guards to prevent images too large (file size, dimensions or pixel count), the wrong content-type type, or the 
  wrong content using ML-powered image classification (SigLIP2)
- Support for JPEG, PNG, WebP, HEIC, AVIF, Jpeg XL, and GIF as well as efficient format conversion between all types
- Support for animated GIF and WebP
- Powerful and secure redirection capabilities

## When you should consider Konifer

- You're about to add images into your application for the first time
- You have several different places in your backend that handle different images (a thumbnail service, an upload service, etc)
- Your service has crashed trying to accept an image that was too large or the wrong file-type (for example, by 
  using Java's `BufferedImage`)
- Managing your images is draining your engineering resources
- You cannot or do not want to use a SaaS platform

## Try It

```bash
docker run -e IN_MEMORY=true -p 8080:8080 ghcr.io/dmaiken/konifer:latest
```

Then upload an image:

```bash
curl --request POST \
  --url 'http://localhost:8080/assets/my-images/' \
  --header 'Content-Type: multipart/form-data' \
  --form 'metadata={"alt":"moon"}' \
  --form file=/path/to/your/image.png
```

Fetch it back, transformed on demand:

```bash
curl --request GET \
  --url 'http://localhost:8080/assets/my-images/-/content?w=800&format=webp'
```

The in-memory mode is for development and evaluation only. For persistent deployments, configure PostgreSQL plus
S3-compatible or filesystem storage.

## Upload Rules

Konifer lets your define prompt collections (ensembles) to be tested against uploaded images. Inference is done
in-process by Google's SigLIP2 vision-language model, so images never leave the server.

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

## Rule Evaluation API 

Konifer includes a Rule Evaluation API for testing rule definitions against real images before adding
them to your Upload Rules. It can also be used independently to classify images without the need to store the image
in Konifer. Each response reports whether the rule matched, its overall score, and the score for every prompt, making 
it easier to tune prompts and thresholds with representative content.

Enable the API explicitly in `konifer.conf`:

```hocon
api {
  rule-evaluation {
    enabled = true
  }
}
```

Then submit up to ten rule definitions with an image URL:

```bash
curl --request POST \
  --url 'http://localhost:8080/rule-evaluations' \
  --header 'Content-Type: application/json' \
  --data '{
    "url": "https://example.com/image.jpg",
    "definitions": [
      {
        "name": "outdoor-landscape",
        "prompts": ["a mountain", "a forest", "an outdoor landscape"],
        "threshold": 0.7
      }
    ]
  }'
```

Inference features require the model to be installed. Install
the model pack using `./scripts/download-siglip2-models.sh` as described in
[Running With Docker Compose](#running-with-docker-compose).

The model is only loaded into memory if the Rule Evaluation API is enabled or rule definitions are defined. Text
embeddings are cached on first-use.

## Documentation

The full documentation is available at [konifer.io](https://konifer.io/).

Release-over-release latency and mixed-load results are available in the
[interactive performance report](https://dmaiken.github.io/konifer/performance/report/).

> [!NOTE] 
> Performance testing on AWS hardware is on the road map and will replace a laptop as the testing hardware.

Useful starting points:

- [Getting started](https://konifer.io/docs/start-here/getting-started)
- [Path configuration](https://konifer.io/docs/concepts/concepts-path-configuration)
- [Asset storage and retrieval concepts](https://konifer.io/docs/concepts/Assets/concepts-assets)
- [Image transformation reference](https://konifer.io/docs/reference/image-transformation-reference)
- [Storage configuration](https://konifer.io/docs/reference/reference-variant-storage)
- [HTTP caching](https://konifer.io/docs/reference/http-caching)
- [URL signing](https://konifer.io/docs/reference/url-signing)

## Development

For normal use, run Konifer in Docker. Local development requires libvips to be installed in a way that matches the
container environment as closely as possible.

```bash
chmod +x ./scripts/install-vips.sh
./scripts/install-vips.sh --with-deps
```

Some service tests and upload content rules use SigLIP2 ONNX models. Download the local model pack once before running
those tests:

```bash
./scripts/download-siglip2-models.sh
```

This creates `models/siglip2-base-patch16-224` at the repository root. The directory is ignored by Git and reused by
local Gradle runs.

Common Gradle tasks:

| Task                              | Description                                                          |
|-----------------------------------|----------------------------------------------------------------------|
| `./gradlew test`                  | Run tests                                                            |
| `./gradlew build`                 | Build the project                                                    |
| `./gradlew :service:shadowJar`    | Build the executable server JAR used by the Docker image             |
| `./gradlew run`                   | Run the server locally                                               |
| `./gradlew ktlintFormat detekt`   | Format and lint the codebase                                         |
| `./gradlew generateJooq`          | Regenerate JOOQ code after schema changes or JOOQ dependency updates |
| `./gradlew generateLicenseReport` | Generate the OSS license report                                      |

If you change the database schema or update JOOQ, run:

```bash
./gradlew generateJooq
```

The generator starts a PostgreSQL testcontainer, applies migrations, runs JOOQ against the resulting schema, and writes
generated code into the `jooq-generated` module.

## macOS Notes

If the libvips installer fails with `Compiler cc cannot compile programs`, install Xcode Command Line Tools:

```bash
xcode-select --install
```

If Gradle or Docker image builds fail because Java cannot be found, install Temurin and set `JAVA_HOME`:

```bash
brew install --cask temurin@25
export JAVA_HOME=$(/usr/libexec/java_home)
```

On Apple Silicon, build the Docker image locally to get a native `arm64` image.

If your configuration uses upload content rules, download the SigLIP2 model pack before starting Compose:

```bash
./scripts/download-siglip2-models.sh
```

Then mount `./models/siglip2-base-patch16-224` into the container at `/app/models/siglip2-base-patch16-224`.

Build the local base image, which contains Temurin JDK 25 and libvips:

```bash
docker build -f Dockerfile.base -t konifer-base:latest .
```

Rebuild the base image whenever `Dockerfile.base` or the libvips installation scripts change. Then build the Konifer
application image:

```bash
./gradlew :service:shadowJar
docker build . -t ghcr.io/dmaiken/konifer:latest
```

Then start the stack:

```bash
docker compose up
```

The default sample configuration in `konifer.conf` targets the Compose services and stores objects in the
`konifer-assets` MinIO bucket.

## Acknowledgments

A huge thank-you to these amazing open-source projects:

- **[libvips](https://github.com/libvips/libvips)**: _The_ cutting-edge, demand-driven image processor for
  high-performance image processing.
- **[vips-ffm](https://github.com/lopcode/vips-ffm)**: The Java FFM bindings that Konifer uses to interact with the
  libvips API.
- **[jOOQ](https://github.com/jooq/jooq)**: The best way to interact with a DB in the JVM environment.
- **[ktor](https://github.com/ktorio/ktor)**: A simple and robust non-blocking web framework for Kotlin.
- **[Onnx](https://onnxruntime.ai/)**: In-process model inference.

## Contact me

Questions or feedback? Email me at [daniel@konifer.io](mailto:daniel@konifer.io) or start a discussion in GitHub.

## License

Konifer is released under the AGPL license in [LICENSE](LICENSE).
