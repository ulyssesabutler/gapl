plugins {
    kotlin("jvm") version "2.0.21"
    application
}

application {
    mainClass.set("com.uabutler.netfpgasimtest.MainKt")
    applicationName = "sim-kernel-test"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":analyzer"))
    implementation(project(":simengine"))
    // PortInspector/PortShape/RootModuleResolver - see interpreter/build.gradle.kts for why these
    // live in simgen despite having no KotlinPoet/codegen dependency of their own.
    implementation(project(":simgen"))
    implementation(project(":simtrace"))
    implementation(project(":vcd"))
    implementation("com.github.ajalt.clikt:clikt:5.0.0")
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
