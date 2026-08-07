plugins {
    application
}

dependencies {
    implementation(project(":application"))
    implementation(project(":engine-codemaat"))
    implementation(project(":engine-architecture"))
    implementation(project(":engine-static-analysis"))
    implementation(project(":integration"))
    implementation(libs.picocli)
    implementation(libs.slf4j.simple)
    annotationProcessor(libs.picocli.codegen)

    testImplementation(project(":testkit"))
}

application {
    mainClass.set("com.debthunter.cli.DebtHunterCli")
}
