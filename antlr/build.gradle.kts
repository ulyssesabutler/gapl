import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.strumenta.antlr-kotlin") version "1.0.0"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.strumenta:antlr-kotlin-runtime:1.0.0")
}

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    source = fileTree("src/main/antlr") {
        include("**/*.g4")
    }

    packageName = "com.uabutler.parsers.generated"
    // We want visitors alongside listeners.
    // The Kotlin target language is implicit, as is the file encoding (UTF-8)
    arguments = listOf("-visitor")
    outputDirectory = layout.buildDirectory.dir("generated").get().asFile
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(generateKotlinGrammarSource)
}

sourceSets.main {
    kotlin {
        srcDirs("build/generated")
    }
}