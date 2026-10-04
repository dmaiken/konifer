# Developing Konifer

Konifer ships as a Docker image. Local development requires libvips to be installed in a way that matches the
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

This creates `models/siglip2-base-patch16-224` at the repository root. Git ignores the directory, and local Gradle
runs reuse it.

## Common tasks

| Task                              | Description                                                          |
|-----------------------------------|----------------------------------------------------------------------|
| `./gradlew test`                  | Run tests                                                            |
| `./gradlew build`                 | Build the project                                                    |
| `./gradlew :service:shadowJar`    | Build the executable server JAR used by the Docker image             |
| `./gradlew :client:assemble`      | Build the client JAR, sources, and API documentation                 |
| `./gradlew run`                   | Run the server locally                                               |
| `./gradlew ktlintFormat detekt`   | Format and lint the codebase                                         |
| `./gradlew generateJooq`          | Regenerate JOOQ code after schema changes or JOOQ dependency updates |
| `./gradlew generateLicenseReport` | Generate the OSS license report                                      |
| `./scripts/scan-image.sh`         | Scan the local `latest` image with Trivy                             |

If you change the database schema or update JOOQ, run `./gradlew generateJooq`. The generator starts a PostgreSQL
testcontainer, applies migrations, runs JOOQ against the resulting schema, and writes generated code into the
`jooq-generated` module.

## Clean base image rebuilds

To refresh the Docker base images after an OS security update, open GitHub Actions, select **Build Base Image**, and
choose **Run workflow** on `main`. Enable **Rebuild runtime and CI base images without Docker layer cache** before
starting the run.

This pulls the current Ubuntu image and rebuilds all Docker layers for both the runtime and CI base images on
`amd64` and `arm64`. The workflow publishes the rebuilt images to GHCR under the existing commit and `latest` tags
and refreshes the build cache. Ordinary builds continue to use the cache unless this option is enabled.

After the base workflow succeeds, rerun the application build or release workflow so the application image uses
the refreshed base, then check its Trivy scan. A clean rebuild picks up available updates; it does not guarantee
that every inherited package is upgraded or that every reported vulnerability has a published fix.

## Client artifacts

Run `./gradlew :client:assemble` to build the JVM client artifacts in `client/build/libs`:

- `konifer-client-jvm-<version>.jar`
- `konifer-client-jvm-<version>-sources.jar`
- `konifer-client-jvm-<version>-javadoc.jar` (Dokka HTML API documentation)

The client compiles the shared model sources from `common/src/commonMain/kotlin` into its own artifact.
The sources and documentation include those models as well.

The JVM client targets Java 17. The service uses the project's Java 25 toolchain.

The client has two Maven publications: `io.konifer:konifer-client` (multiplatform metadata) and
`io.konifer:konifer-client-jvm` (JVM implementation). Both include sources, Dokka HTML documentation
with the `javadoc` classifier, and POM metadata. The publishing plugin generates and attaches the
documentation JARs; no separate custom Javadoc archive is needed.

To inspect the generated publication metadata without uploading artifacts or requiring signing credentials:

```bash
./gradlew :client:assemble \
  :client:generatePomFileForJvmPublication \
  :client:generatePomFileForKotlinMultiplatformPublication \
  :client:generateMetadataFileForJvmPublication \
  :client:generateMetadataFileForKotlinMultiplatformPublication \
  :client:checkPomFileForJvmPublication \
  :client:checkPomFileForKotlinMultiplatformPublication
```

The generated POM and Gradle module metadata files are in `client/build/publications/jvm` and
`client/build/publications/kotlinMultiplatform`.

## Client releases

The client is versioned independently of the service. Pushing a tag such as `client-v0.1.0` runs
`.github/workflows/client-release.yml`; service releases continue to use `v*` tags.
Client tags must contain a SemVer version, optionally with a prerelease suffix such as
`client-v0.2.0-rc.1`. Snapshot versions and build metadata (`+...`) are not accepted by the release workflow.

The workflow takes the client version from the tag, runs the client checks (including JVM tests,
lint, and the committed ABI baseline), validates both POMs, and assembles the publications. It then signs
and publishes both artifacts to Maven Central, waits for the deployment to be published, and creates a
GitHub release. Client releases do not replace the service's latest GitHub release.

To check a candidate version locally without publishing:

```bash
./gradlew :client:check :client:assemble -PclientVersion=0.2.0-rc.1
```

The client's default development version remains in `client/build.gradle.kts`. The `clientVersion`
Gradle property overrides only the client version, including both Maven publications and their archives.
Review and commit intentional ABI baseline updates before tagging; the release workflow does not
regenerate the baseline. Published Maven Central versions cannot be overwritten.

## macOS notes

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
./scripts/scan-image.sh
```

The Trivy scan checks OS and bundled library vulnerabilities. It fails for fixable HIGH or CRITICAL findings using the
shared policy in `trivy.yaml`. Pass another image reference as the first argument to scan a different tag.

Then start the stack:

```bash
docker compose up
```

The default sample configuration in `konifer.conf` targets the Compose services and stores objects in the
`konifer-assets` MinIO bucket.
