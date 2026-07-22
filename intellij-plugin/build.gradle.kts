plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Supports LspServerSupportProvider (available since 2023.2) - a specific, well-established
        // release chosen for reliable artifact resolution, not because anything newer is unsupported.
        intellijIdea("2024.3")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "232" // 2023.2, no untilBuild cap
        }
    }
}

kotlin {
    jvmToolchain(17)
}

val copyServerJar by tasks.registering(Copy::class) {
    // Bundles the server jar as a plugin resource so buildPlugin's zip is self-contained - a
    // friend installing it "from disk" gets a working plugin with no repo clone, no Gradle, and
    // (since the IDE always ships its own JVM) no separate Java install either. See
    // GaplLspServerDescriptor.kt, which extracts this resource to disk before running it.
    dependsOn(":lsp:shadowJar")
    from(rootProject.file("lsp/build/libs/gapl-lsp.jar"))
    into("src/main/resources/server")
}

tasks.named("processResources") {
    dependsOn(copyServerJar)
}

tasks.named("assemble") {
    dependsOn(":lsp:shadowJar")
}

tasks.named<JavaExec>("runIde") {
    // Mirrors vscode-extension's dev-mode server path resolution: for local runIde testing,
    // point GaplLspServerDescriptor straight at the sibling :lsp subproject's shadow-jar output
    // rather than relying on the bundled-resource extraction path (which needs a real packaged
    // plugin jar to extract from).
    dependsOn(":lsp:shadowJar")
    systemProperty("gapl.lsp.jar", rootProject.file("lsp/build/libs/gapl-lsp.jar").absolutePath)
}
