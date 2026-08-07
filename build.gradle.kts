import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.spotbugs.snom.SpotBugsExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.diffplug.spotless") version "6.25.0" apply false
    id("com.github.spotbugs") version "6.0.15" apply false
    jacoco
}

allprojects {
    group = "com.debthunter"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.github.spotbugs")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    val mainSourceSet = the<SourceSetContainer>().getByName("main")
    val testSourceSet = the<SourceSetContainer>().getByName("test")
    val integrationTestSourceSet =
        the<SourceSetContainer>().create("integrationTest") {
            java.srcDir("src/integrationTest/java")
            resources.srcDir("src/integrationTest/resources")
            compileClasspath += mainSourceSet.output + testSourceSet.output
            runtimeClasspath += mainSourceSet.output + testSourceSet.output
        }

    configurations.getByName("integrationTestImplementation") {
        extendsFrom(configurations.getByName("testImplementation"))
    }
    configurations.getByName("integrationTestRuntimeOnly") {
        extendsFrom(configurations.getByName("testRuntimeOnly"))
    }

    val integrationTest =
        tasks.register<Test>("integrationTest") {
            description = "Runs integration tests."
            group = "verification"
            testClassesDirs = integrationTestSourceSet.output.classesDirs
            classpath = integrationTestSourceSet.runtimeClasspath
            useJUnitPlatform()
            shouldRunAfter(tasks.named("test"))
        }

    tasks.named("check") { dependsOn(integrationTest) }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    extensions.configure<SpotlessExtension> {
        java {
            googleJavaFormat()
            target("src/*/java/**/*.java")
        }
        kotlinGradle { target("*.gradle.kts") }
    }

    extensions.configure<SpotBugsExtension> { ignoreFailures.set(true) }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("jacocoTestReport"))
        violationRules { rule { limit { minimum = "0.80".toBigDecimal() } } }
    }

    tasks.named("check") { dependsOn(tasks.named("jacocoTestCoverageVerification")) }

    val catalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    fun lib(alias: String) = catalog.findLibrary(alias).get()

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.11.4"))
        add("testImplementation", lib("junit-jupiter"))
        add("testRuntimeOnly", lib("junit-platform-launcher"))
        add("testImplementation", lib("assertj-core"))
        add("testImplementation", lib("mockito-core"))
        add("testImplementation", lib("mockito-junit-jupiter"))
        add("compileOnly", lib("spotbugs-annotations"))
    }
}
