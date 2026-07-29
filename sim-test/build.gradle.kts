import java.io.File

plugins {
    kotlin("jvm") version "2.0.21"
}

repositories {
    mavenCentral()
}

dependencies {
    // All needed as `implementation`, not just :simgen — the *generated* wrapper classes
    // (compiled as part of this project's own main source set) directly reference
    // com.uabutler.Analyzer (:analyzer), com.uabutler.simengine.Engine (:simengine), and now
    // com.uabutler.vcd.VcdWriter/com.uabutler.simtrace.VcdTracer (Phase 6 VCD tracing support),
    // and `implementation` deps aren't transitively exposed to consumers.
    implementation(project(":analyzer"))
    implementation(project(":simengine"))
    implementation(project(":vcd"))
    implementation(project(":simtrace"))
    implementation(project(":simgen"))
    testImplementation(kotlin("test"))
}

val testsRoot = file("tests")

/** One sim-test/tests/<name>/ directory that has opted into Kotlin simulation via a test.kt harness. */
data class HarnessCase(val name: String, val dir: File) {
    val gaplFile get() = File(dir, "test.gapl")
    val sanitizedName get() = name.replace('-', '_')

    // Every sim-test fixture's top-level GAPL function is generically named "test" — relying on
    // WrapperGenerator's own auto-derived package/class name would collide across every fixture,
    // so package/class are always passed explicitly here, derived from the directory name instead.
    val packageName get() = "com.uabutler.simgen.generated.$sanitizedName"
    val className get() = name.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) } + "Simulator"
}

fun discoverHarnessCases(): List<HarnessCase> =
    testsRoot.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedBy { it.name }
        ?.map { HarnessCase(it.name, it) }
        ?.filter { File(it.dir, "test.kt").exists() }
        .orEmpty()

val generatedDir = layout.buildDirectory.dir("generated")

val generateWrappers by tasks.registering {
    group = "sim-test"
    description = "Generate Kotlin simulation wrapper classes for sim-test fixtures that opt in via a test.kt harness"
    dependsOn(":simgen:installDist")

    inputs.files(fileTree(testsRoot) { include("*/test.gapl", "*/test.kt") })
    outputs.dir(generatedDir)

    doLast {
        val simgenCli = project(":simgen").layout.buildDirectory.file("install/simgen/bin/simgen").get().asFile
        if (!simgenCli.exists()) throw GradleException("simgen CLI not found at $simgenCli")
        val outDir = generatedDir.get().asFile
        outDir.mkdirs()

        discoverHarnessCases().forEach { case ->
            println("Generating wrapper for ${case.name}")
            val result = exec {
                isIgnoreExitValue = true
                commandLine(
                    simgenCli.absolutePath, case.gaplFile.absolutePath,
                    "--output-dir", outDir.absolutePath,
                    "--package", case.packageName,
                    "--class", case.className,
                    // Every fixture's top-level GAPL function is named "test" (see HarnessCase's own
                    // doc comment), but larger designs (aes, md5) define many internal helper
                    // functions - if any turns out to be unreferenced dead code it would register as
                    // an extra root module and break WrapperGenerator's single-root auto-selection.
                    // Passing --module explicitly sidesteps that risk entirely.
                    "--module", "test",
                )
            }
            if (result.exitValue != 0) throw GradleException("Failed to generate wrapper for ${case.name}")
        }
    }
}

sourceSets {
    main {
        kotlin.srcDir(generatedDir)
    }
    test {
        // Pulls every sim-test/tests/<name>/test.kt harness in directly from this project's own
        // tests/ tree — Kotlin doesn't require directory-path-to-package matching, and a
        // SourceDirectorySet only picks up recognized extensions (.kt), so the sibling
        // .gapl/.cpp/.properties/.yaml files are automatically ignored. IMPORTANT: only
        // kotlin.srcDir, not resources.srcDir.
        kotlin.srcDir(testsRoot)
    }
}

// generateWrappers becomes a KotlinCompile input, so test/build already depend on it transitively —
// no extra `tasks.named("build") { dependsOn(...) }` wiring needed, unlike verilator-test (a plain
// `base`-plugin project with no Kotlin compile graph of its own to hook into).
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(generateWrappers)
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }

    // Lets test.kt harnesses locate sim-test/tests/<name>/test.gapl reliably regardless of the test
    // task's own working directory.
    systemProperty("simTestRoot", project.projectDir.absolutePath)
}

// Mirrors :verilator-test:runSimulation's naming convention: one named, discoverable entry point
// that generates wrappers, builds, and runs everything end-to-end.
tasks.register("runSimulation") {
    group = "sim-test"
    description = "Generate wrappers, build, and run all sim-test fixtures end-to-end"
    dependsOn("test")
}

kotlin {
    jvmToolchain(17)
}
