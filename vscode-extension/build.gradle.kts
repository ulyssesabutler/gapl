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

tasks.named("assemble") {
    dependsOn(npmCompile)
}

tasks.named<Delete>("clean") {
    delete("out", "node_modules")
}
