plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "debt-hunter"

include(
    "cli",
    "application",
    "domain",
    "engine-spi",
    "engine-codemaat",
    "engine-architecture",
    "engine-static-analysis",
    "repository",
    "policy",
    "output",
    "integration",
    "ai",
    "testkit",
)
