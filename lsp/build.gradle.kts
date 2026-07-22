plugins {
    kotlin("jvm") version "2.0.21"
    application
    id("com.gradleup.shadow") version "8.3.6"
}

application {
    mainClass.set("com.uabutler.lsp.MainKt")
    applicationName = "gapl-lsp"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":analyzer"))
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.shadowJar {
    // Fixed, predictable name/no version suffix - both editor plugins bundle and invoke this
    // exact path directly (`java -jar gapl-lsp.jar`), so it can't shift between builds.
    archiveBaseName.set("gapl-lsp")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}
