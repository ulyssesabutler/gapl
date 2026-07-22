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

tasks.named("assemble") {
    // gapl-lsp's dev-mode server path fallback points straight at ../lsp's install output, so
    // building this plugin should guarantee that actually exists - same rationale as vscode-extension.
    dependsOn(":lsp:installDist")
}

tasks.named<JavaExec>("runIde") {
    // Mirrors vscode-extension's dev-mode server path resolution: for local runIde testing,
    // point GaplLspServerDescriptor straight at the sibling :lsp subproject's install output
    // rather than requiring gapl-lsp to be on PATH (which a real, packaged install would provide).
    dependsOn(":lsp:installDist")
    systemProperty("gapl.lsp.path", rootProject.file("lsp/build/install/gapl-lsp/bin/gapl-lsp").absolutePath)
}
