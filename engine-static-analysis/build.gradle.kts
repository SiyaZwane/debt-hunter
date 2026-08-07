dependencies {
    api(project(":engine-spi"))
    implementation(libs.jackson.databind)

    testImplementation(project(":testkit"))
}
