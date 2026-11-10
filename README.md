<p align="center">
  <img src="https://konifer.io/img/konifer-small.png" alt="Konifer logo" width="100"/>
</p>

# Konifer

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/dmaiken/konifer/build.yml)
![Codecov](https://img.shields.io/codecov/c/github/dmaiken/konifer)
![GitHub License](https://img.shields.io/github/license/dmaiken/konifer)

**The self-hosted image backend your application doesn't have to build.**

Adding image uploads means more than putting files in a bucket. Your team needs validation, thumbnails, replacement
behavior, metadata, cache rules, and deletion across storage and application records. Konifer provides those
capabilities through one HTTP API so you can spend less time building and maintaining image infrastructure.

Use your storage and CDN, and choose image paths that fit your application. Address a profile picture by user, a
gallery by product, or an individual image by ID. Group related images under shared paths to apply storage and
processing policies and delete them together.

Deploy Konifer alongside PostgreSQL and S3-compatible storage or a persistent filesystem. Image processing and
background variant generation run inside Konifer.

[10-minute quickstart](https://konifer.io/docs/start-here/getting-started) ·
[Documentation](https://konifer.io/) ·
[Deployment](https://konifer.io/docs/operate/deployment) ·
[Performance report](https://dmaiken.github.io/konifer/performance/report/)

## Why I built it

I've seen the same story on several teams: someone needs to store a product photo, then another feature needs
thumbnails, then a third needs different upload limits. Before long, image handling is spread across S3 buckets,
Lambdas, queues, and services. Replacing a photo, keeping its history, or deleting a user's images means coordinating
those pieces again.

With Konifer, you configure those jobs in one service and keep the image handling code out of individual applications:

- **Validate uploads in one place:** accept multipart uploads, S3 object ARN imports, or URLs from allowed domains.
  Configure upload size, image dimension, pixel count, and format limits instead of repeating validation in callers.
- **Use a built-in replacement and history model:** keep originals, metadata, and upload history at application-defined
  paths. Replace a photo without changing its application URL, and retrieve previous entries when you need them.
- **Reuse transformations and cached results:** resize, crop, and convert images with libvips. Define named profiles
  and generate variants after upload or on demand, without building a separate thumbnail-generation workflow.
- **Centralize delivery and cleanup:** request content, metadata, links, or redirects to your object store or CDN.
  Delete an entry, the images at a path, or a path and its children through the API instead of implementing image
  storage cleanup in each application.

You can give avatars, marketplace listings, and tenant branding different storage and processing policies while
running the same service.

## Try it

Run the 1.0 release candidate with in-memory storage:

```bash
docker run --name konifer-quickstart \
  --publish 8080:8080 \
  --env IN_MEMORY=true \
  ghcr.io/dmaiken/konifer:1.0.0-rc.1
```

In-memory mode loses all uploaded images and metadata when the container stops.

Follow the [10-minute quickstart](https://konifer.io/docs/start-here/getting-started) to upload, transform, and replace
your first image.

## Choose image paths that fit your application

You define the paths you use to store and retrieve images. Use a resource path such as
`/assets/users/123/profile-picture` to address a user's current picture. Construct its URL from the user ID without
storing a separate image reference:

```http
POST /assets/users/123/profile-picture
GET  /assets/users/123/profile-picture/-/content?width=256&format=webp
GET  /assets/users/123/profile-picture/-/info
```

Use an image ID of your choosing when you want an independent image reference. Combine image IDs with resource paths
to organize individual images within a user's collection:

```http
POST /assets/{imageId}
GET  /assets/{imageId}/-/content

POST /assets/users/123/images/{imageId}
GET  /assets/users/123/images/{imageId}/-/content
```

Each upload gets a Konifer `entryId` within its chosen path. Use the path to fetch the current image or select an entry
to retrieve a specific version:

```http
GET /assets/users/123/profile-picture/-/entry/0/content
```

Shared parent paths give you a way to configure policies and manage related images. Apply upload limits to user
images, choose a bucket for profile pictures, or delete a user's images through one request. If user 123 closes their
account, delete the images below `users/123` without assembling a list of individual image IDs:

```http
POST   /assets/users/123/profile-picture
POST   /assets/users/123/images/{imageId}
DELETE /assets/users/123/-/recursive
```

See the [asset model](https://konifer.io/docs/concepts/Assets/concepts-assets) for collections, upload history, and
entry selection.

## Keep image policies out of application code

Define storage and processing rules in `konifer.conf` so callers can upload and fetch images without repeating the
policy for each image category. This example limits user images to 15 megapixels, stores profile pictures in a
dedicated bucket, and generates a WebP thumbnail after upload. Requests for transformed profile pictures must use the
named profile:

```hocon
variant-profiles {
  thumbnail {
    width = 256
    height = 256
    fit = fill
    rotate = auto
    format = webp
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
}
```

Mount your configuration at `/app/config/konifer.conf`. With S3-compatible storage, create the `profiles` bucket before
using this example. See the [deployment guide](https://konifer.io/docs/operate/deployment) for the storage setup.

Fetch the thumbnail with:

```http
GET /assets/users/123/profile-picture/-/content?profile=thumbnail
```

Profile pictures inherit the pixel limit from `/users/**`. Add more specific rules for other image categories to
choose their buckets, accepted formats, variant generation, caching, and delivery behavior. The
[path configuration docs](https://konifer.io/docs/concepts/concepts-path-configuration) cover matching and inheritance.

## Run it in your infrastructure

Konifer ships as a Docker image for Linux `amd64` and `arm64`. A persistent deployment needs Konifer, PostgreSQL for
asset records, and S3-compatible storage or a filesystem for originals and generated variants. If your
team already operates PostgreSQL and object storage, you can use that infrastructure for Konifer.

Image processing, background variant generation, and inference (if enabled) run inside the Konifer container. Put your CDN in front
for image delivery.

Start with one instance and scale horizontally by adding replicas behind a load balancer. Instances share PostgreSQL
and image storage. Each instance is stateless. Filesystem deployments require a shared persistent mount accessible to
all replicas.

| Deployment concern                                     | Documentation                                                                                                                   |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| Container setup, networking, replicas, and upgrades    | [Deployment guide](https://konifer.io/docs/operate/deployment)                                                                  |
| PostgreSQL and image storage                           | [Storage configuration](https://konifer.io/docs/reference/reference-variant-storage)                                            |
| CPU, memory, temporary storage, and worker concurrency | [Resource tuning](https://konifer.io/docs/reference/image-processing-reference)                                                 |
| CDN behavior and fetch URL protection                  | [HTTP caching](https://konifer.io/docs/reference/http-caching) and [URL signing](https://konifer.io/docs/reference/url-signing) |

Konifer fits teams that want to own the image lifecycle for avatars, product galleries, tenant branding, or other
application assets. If you already have source-image URLs and just want on-the-fly resizing and format conversion, consider an image
processor such as [imgproxy](https://imgproxy.net/) or [imagor](https://docs.imagor.net/). Choose a managed image platform
if you want a provider to host and manage your delivery infrastructure. See
[Who should use Konifer?](https://konifer.io/docs/start-here/is-konifer-right-for-you) for more detail.

## Testing, security, and performance

- The [build workflow](.github/workflows/build.yml) runs unit and functional tests, then integration tests against the
  containerized application with PostgreSQL and S3-compatible storage. The tests exercise native image processing and
  model inference alongside the HTTP API.
- The [image security workflow](.github/workflows/image-security.yml) generates an SPDX software bill of materials
  and scans it for vulnerabilities. The [image policy workflow](.github/workflows/image-policy.yml) checks licenses and
  container configuration.
- The [performance report](https://dmaiken.github.io/konifer/performance/report/) tracks latency across releases
  for common image operations.

## Additional capabilities

**Image formats and placeholders.** Process JPEG, PNG, WebP, HEIC, AVIF, JPEG XL, and GIF, including animated GIF and
WebP. Generate BlurHash and ThumbHash placeholders. See the
[transformation reference](https://konifer.io/docs/reference/image-transformation-reference).

**Pixels for vision workers.** Request encoded display images and resized raw pixel buffers from the same stored
source. Named profiles define dimensions, orientation, color space, channel order, and HWC or CHW layout. Workers
handle tensor construction and model-specific scaling and normalization. See the
[raw pixel contract](https://konifer.io/docs/concepts/concepts-pixels) and
[PyTorch guide](https://konifer.io/docs/guides/guides-pixels-for-inference).

**Content classification.** Install the optional SigLIP2 model pack to accept, reject, or label uploads using text
prompts, with inference inside the server process. Test prompts and thresholds through the
[Rule Evaluation API](https://konifer.io/docs/concepts/concepts-rule-evaluation) before applying
[upload rules](https://konifer.io/docs/concepts/concepts-upload-rules).

## Development

Konifer ships as a Docker image. For local development, install the native dependencies and download the SigLIP2
model pack if you work on inference or its tests. [CONTRIBUTING.md](CONTRIBUTING.md) has the setup steps, Gradle tasks,
and macOS notes.

## Acknowledgments

Konifer builds on these open-source projects:

- **[libvips](https://github.com/libvips/libvips)**: The image processor behind Konifer's transformations.
- **[vips-ffm](https://github.com/lopcode/vips-ffm)**: The Java FFM bindings Konifer uses to call libvips.
- **[jOOQ](https://github.com/jooq/jooq)**: Database access and generated query code.
- **[Ktor](https://github.com/ktorio/ktor)**: The Kotlin server framework.
- **[ONNX Runtime](https://onnxruntime.ai/)**: In-process model inference.

## Contact me

Evaluating Konifer for your application? Email me at [daniel@konifer.io](mailto:daniel@konifer.io) or
[start a discussion](https://github.com/dmaiken/konifer/discussions). I'd like to hear what you're building and what
would block adoption.

## License

The Konifer server is released under the AGPL license in [LICENSE](LICENSE).

The Konifer client and common module are licensed under Apache 2.0 license in [LICENSE](client/LICENSE) and
[LICENSE](common/LICENSE), respectively.
