plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "konifer"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("service")
// include("integration")
include("codegen")
include("jooq-generated")
