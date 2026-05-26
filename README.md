<p align="center">
  <img src="https://konifer.io/img/konifer-small.png" alt="Konifer logo" width="200"/>
</p>

# Konifer

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/dmaiken/konifer/build.yml)
![Codecov](https://img.shields.io/codecov/c/github/dmaiken/konifer)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
![GitHub License](https://img.shields.io/github/license/dmaiken/konifer)

Konifer is a self-hosted image storage, transformation, and delivery API for teams that want Cloudinary- or Imgix-style capabilities without shaping their application around a vendor's asset IDs, pricing model, or storage choices.

It stores original images, generates and caches transformed variants, and returns content, links, redirects, downloads, or metadata from a single HTTP API. The core idea is simple: Konifer's URLs can follow your domain model.

```http
POST /assets/users/123/profile-picture
GET  /assets/users/123/profile-picture/-/content?w=256&format=webp
GET  /assets/users/123/profile-picture/-/metadata
```

Instead of storing `imageId = 2c3ee9c4-58b4-4d0c-8694-ab91125b5d3a` in your user service, you can address the image where it naturally belongs: `/assets/users/123/profile-picture`.

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

The in-memory mode is for development and evaluation only. For persistent deployments, configure PostgreSQL plus S3-compatible or filesystem storage.

## Why Konifer Exists

Most hosted image platforms solve the hard parts of image delivery, but they also tend to introduce a new source of truth. Your application uploads a file, receives an opaque identifier, stores that identifier somewhere, then uses it later to ask the image service what to do.

Konifer is built around a zero-state integration model. Your application does not need to persist Konifer-specific IDs just to render an image later. If your product already knows the user, post, organization, tenant, or document that owns an image, that knowledge is enough to construct the image URL.

```http
POST /assets/organizations/acme/users/123/avatar
GET  /assets/organizations/acme/users/123/avatar/-/content
```

Multiple images can still live at the same path. Each stored image receives an `entryId` that is unique within that path, so the path can represent the domain concept while `entryId` can represent a specific version or historical item.

```http
GET /assets/organizations/acme/users/123/avatar/-/entry/4/content
```

## Who Konifer Is For

Konifer is intended for developers and platform teams who want:

- A self-hosted image pipeline with predictable infrastructure costs.
- Image transformation and delivery without sending image processing through a third-party API.
- S3-compatible storage, local filesystem storage, or in-memory storage for development.
- A path-based API that fits an existing domain model instead of forcing a separate image identity model.
- Control over when variants are generated, where they are stored, and how they are returned.
- CDN-friendly behavior, including redirects, cache headers, ETags, and signed URLs.

It is especially useful for products where images already belong to clear domain resources: user avatars, organization logos, marketplace listings, CMS images, documents, galleries, generated media, and user-uploaded content.

## What Konifer Does

Konifer handles the image lifecycle behind an HTTP API:

- Store images from multipart uploads or URLs.
- Store metadata such as `alt`, labels, and tags.
- Fetch the newest image at a path, a specific `entryId`, or multiple matching images.
- Return images as direct content, object-store links, redirects, downloads, or JSON metadata.
- Generate transformed variants on demand and cache them in the configured object store.
- Generate common variants eagerly after upload using named variant profiles.
- Apply per-path rules for storage, validation, preprocessing, eager variants, redirects, caching, and LQIPs.
- Generate low-quality image placeholders using BlurHash and ThumbHash.
- Sign fetch URLs with HMAC to protect public transformation endpoints.

Supported formats include JPEG, PNG, WebP, AVIF, JPEG XL, HEIC, and GIF, with support for animated WebP and GIF.

## The Path Model

Konifer paths are intentionally application-defined. The API does not care whether your hierarchy is user-based, tenant-based, CMS-based, or something else.

```http
POST /assets/users/123/profile-picture
POST /assets/users/123/background
POST /assets/blog/42/posts/5/hero
POST /assets/products/sku-123/gallery
```

Query selectors live after the `/-/` separator. They let you choose the response shape, ordering, limit, or exact entry without making those controls part of your domain path.

```http
GET /assets/users/123/profile-picture/-/link
GET /assets/users/123/profile-picture/-/content
GET /assets/users/123/profile-picture/-/redirect
GET /assets/users/123/profile-picture/-/download
GET /assets/users/123/profile-picture/-/metadata
GET /assets/users/123/profile-picture/-/entry/4/content
```

By default, Konifer returns a `link` response for the newest image at a path.

## Transformations And Variants

A variant is a transformed version of the original image. Konifer can resize, crop, rotate, flip, blur, pad, change formats, adjust quality, strip metadata, and manage color space.

On-demand variants are generated when requested, stored, and reused on later requests:

```http
GET /assets/users/123/profile-picture/-/content?w=300&h=300&fit=crop&g=attention&format=webp
```

Variant profiles let you name transformations that your application uses often:

```hocon
variant-profiles = [
  {
    name = thumbnail
    w = 128
    fit = fill
    r = auto
  }
]
```

```http
GET /assets/users/123/profile-picture/-/content?profile=thumbnail
```

Profiles can also be used for eager variants, where Konifer starts background generation after upload. If an eager variant is not ready when requested, it can still be generated on demand.

## Path Configuration

Different parts of your image hierarchy can behave differently. Path configuration lets you define rules once in `konifer.conf` and apply them with wildcard matching and inheritance.

```hocon
paths = [
  {
    path = "/public/avatars/**"
    eager-variants = [ thumbnail ]
    image {
      lqip = [ blurhash, thumbhash ]
    }
    preprocessing {
      enabled = true
      image {
        max-width = 1024
        max-height = 1024
        fit = fit
      }
    }
    cache-control {
      enabled = true
      visibility = public
      max-age = 31536000
      immutable = true
    }
  }
]
```

This is where Konifer becomes more than a transformation endpoint. Public avatars, private documents, CMS images, and generated media can share the same service while using different buckets, validation rules, preprocessing, cache behavior, redirect strategies, and eager variants.

## Storage And Architecture

Konifer uses a dual-store architecture:

- Object storage holds image bytes and generated variants.
- PostgreSQL stores metadata, path hierarchy, labels, tags, and variant records.

Object storage can be AWS S3, an S3-compatible provider such as MinIO or Cloudflare R2, a mounted filesystem, or in-memory storage for development. PostgreSQL is the production metadata store and uses the `ltree` extension for hierarchical path queries.

The server is built with Kotlin and Ktor, and image processing is powered by libvips. Konifer avoids buffering entire assets in application memory where possible, uses temporary files during processing, and runs variant generation through bounded workers so expensive transformations do not overwhelm the service.

## Documentation

The full documentation is available at [konifer.io](https://konifer.io/).

Useful starting points:

- Getting started
- Asset storage and retrieval concepts
- Path configuration
- Image transformation reference
- Storage configuration
- URL signing
- HTTP caching

## Running With Docker Compose

The included Compose file runs Konifer with PostgreSQL and MinIO. It expects a `konifer.conf` file at the repository root.

Build the local image first:

```bash
./gradlew :service:shadowJar
docker build . -t ghcr.io/dmaiken/konifer:latest
```

Then start the stack:

```bash
docker compose up
```

The default sample configuration in `konifer.conf` targets the Compose services and stores objects in the `konifer-assets` MinIO bucket.

## Acknowledgments

A huge thank-you to these amazing open-source projects:

- **[libvips](https://github.com/libvips/libvips)**: _The_ cutting-edge, demand-driven image processor for high-performance image processing.
- **[vips-ffm](https://github.com/lopcode/vips-ffm)**: The Java FFM bindings that Konifer uses to interact with the libvips API.
- **[jOOQ](https://github.com/jooq/jooq)**: The best way to interact with a DB in the JVM environment.
- **[ktor](https://github.com/ktorio/ktor)**: A simple and robust non-blocking web framework for Kotlin.

## Development

For normal use, run Konifer in Docker. Local development requires libvips to be installed in a way that matches the container environment as closely as possible.

```bash
chmod +x ./scripts/install-vips.sh
./scripts/install-vips.sh --with-deps
```

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

The generator starts a PostgreSQL testcontainer, applies migrations, runs JOOQ against the resulting schema, and writes generated code into the `jooq-generated` module.

## macOS Notes

If the libvips installer fails with `Compiler cc cannot compile programs`, install Xcode Command Line Tools:

```bash
xcode-select --install
```

If Gradle or Docker image builds fail because Java cannot be found, install GraalVM and set `JAVA_HOME`:

```bash
brew install --cask graalvm-jdk@25
export JAVA_HOME=$(/usr/libexec/java_home)
```

On Apple Silicon, build the Docker image locally to get a native `arm64` image.

## License

Konifer is released under the license in [LICENSE](LICENSE).
