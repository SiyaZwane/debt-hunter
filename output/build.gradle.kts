dependencies {
    api(project(":domain"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
}
