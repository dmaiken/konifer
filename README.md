<p align="center">
  <img src="https://konifer.io/img/konifer-small.png" alt="Konifer logo" width="200"/>
</p>

# Konifer

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/dmaiken/konifer/build.yml)
![Codecov](https://img.shields.io/codecov/c/github/dmaiken/konifer)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)](http://kotlinlang.org)
![GitHub License](https://img.shields.io/github/license/dmaiken/konifer)

Konifer is image infrastructure for applications that need to store, transform, and deliver images using their own
domain model.

It stores original images, generates and caches transformed variants, and returns content, links, redirects, downloads,
or asset information from a single HTTP API. The core idea is straightforward: Konifer's URLs can follow your domain model.

```http
POST /assets/users/123/profile-picture
GET  /assets/users/123/profile-picture/-/content?w=256&format=webp
GET  /assets/users/123/profile-picture/-/info
```

Instead of storing `imageId = 2c3ee9c4-58b4-4d0c-8694-ab91125b5d3a` in your user service, you can address the image
where it naturally belongs: `/assets/users/123/profile-picture`.

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

## Why Konifer Exists

Many image platforms introduce a separate identity model for media. Your application uploads a file, receives a separate
identifier, stores that identifier somewhere, then uses it later to ask the image service what to do.

Konifer is built around a zero-state integration model. Your application does not need to persist Konifer-specific IDs
just to render an image later. If your product already knows the user, post, organization, tenant, or document that owns
an image, that knowledge is enough to construct the image URL.

```http
POST /assets/organizations/acme/users/123/avatar
GET  /assets/organizations/acme/users/123/avatar/-/content
```

Multiple images can still live on the same path. Each stored image receives an `entryId` that is unique within that
path, so the path can represent the domain concept while `entryId` can represent a specific version or historical item.

```http
GET /assets/organizations/acme/users/123/avatar/-/entry/4/content
```

## Who Konifer Is For

Konifer is intended for developers and platform teams who want:

- One API for image storage, transformation, and delivery.
- A path-based API that fits an existing domain model instead of forcing a separate image identity model.
- S3-compatible storage, local filesystem storage, or in-memory storage for development.
- Control over when variants are generated, where they are stored, and how they are returned.
- CDN-friendly behavior, including redirects, cache headers, ETags, and signed URLs.
- An image pipeline that fits their existing infrastructure and cost model.

It is especially useful for products where images already belong to clear domain resources: user avatars, organization
logos, marketplace listings, CMS images, documents, galleries, generated media, and user-uploaded content.

## What Konifer Does

Konifer handles the image lifecycle behind an HTTP API:

- Store images from multipart uploads or URLs.
- Store information such as `alt`, labels, and tags.
- Fetch the newest image at a path, a specific `entryId`, or multiple matching images.
- Return images as direct content, object-store links, redirects, downloads, or asset information in JSON.
- Generate transformed variants on demand and cache them in the configured object store.
- Generate common variants eagerly after upload using named variant profiles.
- Apply per-path rules for storage, validation, preprocessing, eager variants, redirects, caching, and LQIPs.
- Generate low-quality image placeholders using BlurHash and ThumbHash.
- Sign fetch URLs with HMAC to protect public transformation endpoints.

Supported formats include JPEG, PNG, WebP, AVIF, JPEG XL, HEIC, and GIF, with support for animated WebP and GIF.

## The Path Model

Konifer paths are intentionally application-defined. The API does not care whether your hierarchy is user-based,
tenant-based, CMS-based, or something else.

```http
POST /assets/users/123/profile-picture
POST /assets/users/123/background
POST /assets/blog/42/posts/5/hero
POST /assets/products/sku-123/gallery
```

Query selectors live after the `/-/` separator. They let you choose the response shape, ordering, limit, or exact entry
without making those controls part of your domain path.

```http
GET /assets/users/123/profile-picture/-/link
GET /assets/users/123/profile-picture/-/content
GET /assets/users/123/profile-picture/-/redirect
GET /assets/users/123/profile-picture/-/download
GET /assets/users/123/profile-picture/-/info
GET /assets/users/123/profile-picture/-/entry/4/content
```

By default, Konifer returns a `link` response for the newest image at a path.

## Transformations And Variants

A variant is a transformed version of the original image. Konifer can resize, crop, rotate, flip, blur, pad, change
formats, adjust quality, strip metadata, and manage color space.

On-demand variants are generated when requested, stored, and reused on later requests:

```http
GET /assets/users/123/profile-picture/-/content?w=300&h=300&fit=crop&g=attention&format=webp
```

Variant profiles let you name transformations that your application uses often:

```hocon
variant-profiles {
  thumbnail {
    w = 128
    fit = fill
    r = auto
  }
}
```

```http
GET /assets/users/123/profile-picture/-/content?profile=thumbnail
```

Profiles can also be used for eager variants, where Konifer starts background generation after upload. If an eager
variant is not ready when requested, it can still be generated on demand.

## Path Configuration

Different parts of your image hierarchy can behave differently. Path configuration lets you define rules once in
`konifer.conf` and apply them with wildcard matching and inheritance.

```hocon
variant-profiles {
  thumbnail {
    w = 256
    fit = fill
  }
}
paths {
  "/public/avatars/**" {
    transform {
      eager-variants = [thumbnail]
      preprocessing {
        enabled = true
        image {
          max-width = 1024
          max-height = 1024
          fit = fit
        }
      }
    }
    image {
      lqip = [blurhash, thumbhash]
    }
    cache-control {
      enabled = true
      visibility = public
      max-age = 31536000
      immutable = true
    }
  }
}
```

This is where Konifer becomes more than a transformation endpoint. Public avatars, private documents, CMS images, and
generated media can share the same service while using different buckets, validation rules, dynamic labeling,
preprocessing, cache behavior, redirect strategies, and eager variants.

## Rule Evaluation API

Konifer includes a production-ready Rule Evaluation API for testing rule definitions against real images before adding
them to your Upload Rules. It can also be used independently to classify images with natural-language prompts. Each
response reports whether the rule matched, its overall score, and the score for every prompt, making it easier to tune
prompts and thresholds with representative content.

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

Image data can also be uploaded directly as multipart form data. Rule evaluation uses the same SigLIP2 model and rule
semantics as Upload Rules, so a definition tested here can be moved into path configuration with confidence. Install
the model pack using `./scripts/download-siglip2-models.sh` as described in
[Running With Docker Compose](#running-with-docker-compose).

## Storage And Architecture

Konifer uses a dual-store architecture:

- Object storage holds image bytes and generated variants.
- PostgreSQL stores asset information, metadata, path hierarchy, labels, tags, and variant records.

Object storage can be AWS S3, an S3-compatible provider such as MinIO or Cloudflare R2, a mounted filesystem, or
in-memory storage for development. PostgreSQL is the production data store and uses the `ltree` extension for
hierarchical path queries.

The server is built with Kotlin and Ktor, and image processing is powered by libvips. Konifer avoids buffering entire
assets in application memory where possible, uses temporary files during processing, and runs variant generation through
bounded workers so expensive transformations do not overwhelm the service.

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

## Running With Docker Compose

The included Compose file runs Konifer with PostgreSQL and MinIO. It expects a `konifer.conf` file at the repository
root.

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

## License

Konifer is released under the AGPL license in [LICENSE](LICENSE).
