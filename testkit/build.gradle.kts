dependencies {
    api(project(":domain"))
    api(libs.jgit)
    api(libs.assertj.core)
    api(libs.junit.jupiter)
    implementation(libs.jackson.databind)
}
