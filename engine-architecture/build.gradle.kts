dependencies {
    api(project(":engine-spi"))
    implementation(libs.snakeyaml)

    testImplementation(project(":testkit"))
}
