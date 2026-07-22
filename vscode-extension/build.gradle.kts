import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    // Downloaded into this project's .gradle/nodejs, independent of any system-installed Node -
    // reproducible across machines without requiring Node/npm to be preinstalled.
    version.set("20.18.1")
    download.set(true)
}

val npmCompile by tasks.registering(NpmTask::class) {
    dependsOn(tasks.npmInstall)
    args.set(listOf("run", "compile"))

    inputs.dir("src")
    inputs.file("tsconfig.json")
    inputs.file("package.json")
    outputs.dir("out")
}

val copyServerJar by tasks.registering(Copy::class) {
    // Bundles the server directly into the extension so a friend who installs the .vsix (no
    // repo clone, no Gradle) gets a working extension out of the box - see resolveServerCommand
    // in src/extension.ts, which checks for this file before falling back to dev-mode paths.
    dependsOn(":lsp:shadowJar")
    from(rootProject.file("lsp/build/libs/gapl-lsp.jar"))
    into("server")
}

tasks.named("assemble") {
    dependsOn(npmCompile, copyServerJar)
}

val packageVsix by tasks.registering(NpmTask::class) {
    dependsOn(tasks.named("assemble"))
    args.set(listOf("run", "package"))

    inputs.dir("out")
    inputs.file("server/gapl-lsp.jar")
    inputs.file("package.json")
    outputs.dir(".")
}

tasks.named<Delete>("clean") {
    delete("out", "node_modules", "server", fileTree(".") { include("*.vsix") })
}
