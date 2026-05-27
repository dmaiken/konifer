plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "konifer"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("common")
include("service")
include("client")
include("integration-test")
include("codegen")
include("jooq-generated")
