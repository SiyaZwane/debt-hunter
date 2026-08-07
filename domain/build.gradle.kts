// domain must never depend on I/O, framework, or engine libraries — see global rule #7.
val forbiddenGroups = setOf(
    "com.fasterxml.jackson.core",
    "com.fasterxml.jackson.datatype",
    "org.eclipse.jgit",
    "org.yaml",
    "info.picocli",
    "org.springframework",
)

configurations.matching { it.name == "compileClasspath" || it.name == "runtimeClasspath" }.configureEach {
    resolutionStrategy.eachDependency {
        require(requested.group !in forbiddenGroups) {
            "domain module must not depend on ${requested.group}:${requested.name} — it must remain pure (no I/O, no framework, no engine libraries)"
        }
    }
}

dependencies {
}
