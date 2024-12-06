import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.strumenta.antlr-kotlin") version "1.0.0"
    application
}

application {
    mainClass.set("com.uabutler.CompileKt")
}

group = "com.uabutler"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.strumenta:antlr-kotlin-runtime:1.0.0")
}

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    // ANTLR .g4 files are under {example-project}/antlr
    // Only include *.g4 files. This allows tools (e.g., IDE plugins)
    // to generate temporary files inside the base path
    source = fileTree(layout.projectDirectory.dir("antlr")) {
        include("**/*.g4")
    }

    // We want the generated source files to have this package name
    val pkgName = "com.uabutler.parsers.generated"
    packageName = pkgName

    // We want visitors alongside listeners.
    // The Kotlin target language is implicit, as is the file encoding (UTF-8)
    arguments = listOf("-visitor")

    // Generated files are outputted inside build/generatedAntlr/{package-name}
    val outDir = "generatedAntlr/${pkgName.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(outDir).get().asFile
}

val gen = generateKotlinGrammarSource

tasks.withType<KotlinCompile> {
    dependsOn(generateKotlinGrammarSource)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
}

sourceSets {
    main {
        kotlin {
            srcDirs("build/generatedAntlr")
        }
    }
}