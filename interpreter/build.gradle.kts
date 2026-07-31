plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

application {
    mainClass.set("com.uabutler.interpreter.MainKt")
    applicationName = "interpreter"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":analyzer"))
    implementation(project(":simengine"))
    // Reuses PortInspector/PortShape/RootModuleResolver, which have no KotlinPoet/codegen dependency
    // of their own - they just happen to live here. Pulling in simgen's KotlinPoet/Clikt as unused
    // transitive weight is preferable to moving those files out of simgen for this alone.
    implementation(project(":simgen"))
    implementation("com.github.ajalt.clikt:clikt:5.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
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
