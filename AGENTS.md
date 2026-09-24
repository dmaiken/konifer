# Project Structure

Project is a Gradle multi-module project for ingestion. transformation, and lifecycle management of images. The README
offers a more complete vision and purpose of the project, as well a common commands for builing, testing, linting, etc.

## Modules

- **client**: an un-released KMP client used by integration and functional tests (the migration to use the client is in-progress)
- **codegen**: small module that uses JOOQ code generator to generate code from migrations in service module.
- **service**: the main part of Konifer. The server executable.
- **common**: models used by both `client` and `service` modules
- **integration-test**: tests that run against the containerized version of Konifer. They leverage the `client` as well.
- **jooq-generated**: where `codegen` module outputs generated JOOQ code.

# Stack

- Ktor
- JOOQ with code generation. Generated code is in `jooq-generated` module
- libvips
- Several native dependencies built from source. Manifest is at `/scripts/native-build/native-versions.env`
- Docker (service is release only as a Docker image; JARs are not released)
- Onnx for runtime inference

# Testing 

Tests are broken up into the following:
- Unit tests: in each module where applicable
- Functional tests: Using the Ktor test harness, test the application preferring the use of in-memory object and data stores. Located in `service` module in `functionalTest` source set
- Integration tests: Runs tests against containerized application with Postgres as data store and MinIO as object store.

Limit mocking and prefer using external dependencies using testcontainers, libvips (assume it's installed), and Onnx.
There is an 85% complete application coverage quality gate. Individual commits/PRs are not gated for coverage unless it
drops the total application coverage below 85%.

Be mindful of running the _entire_ test suite. Because actual dependencies are used, tests take longer. This trade off is
intentional. Don't use `--no-daemon` when running tests using Gradle.

## Integration testing

These tests are designed to ensure that the docker image contains all the necessary runtime dependencies
for supporting Konifer features. Not all things have to be tested, and the assertions must be granular, or it should be
a functional test instead.

The goal here is to test aspects of the platform that may change when the runtime changes from a local development
machine to a constructed docker image (networking, libvips runtimes, etc.).

## Test stack

- kotest for assertions in all modules
- kotest for kmp modules (`client`)
- Junit 5 for JVM modules
- Kover for coverage

# General Guidance

The project attempts to follow DDD architecture, but it is not perfect. Follow the existing domain boundaries when changing behavior.

This is not a Spring Boot project. Explicitness in the code is favored. Prefer Kotlin semantics and avoid java-like Kotlin.
Prefer the use of Value classes to represent domain value objects and the use of sealed classes/interfaces to define state
and control state transitions.

If necessary, lint at the end of the task using: `./gradlew ktlintFormat detekt`
