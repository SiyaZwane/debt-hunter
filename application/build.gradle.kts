dependencies {
    api(project(":domain"))
    api(project(":engine-spi"))
    api(project(":repository"))
    api(project(":policy"))
    api(project(":output"))
    implementation(libs.slf4j.api)

    testImplementation(project(":testkit"))
}
