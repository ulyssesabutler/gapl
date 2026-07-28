plugins {
    kotlin("jvm") version "2.0.21"
    application
}

application {
    mainClass.set("com.uabutler.simgen.GenerateWrapperKt")
    applicationName = "simgen"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":analyzer"))
    implementation(project(":simengine"))
    implementation(project(":vcd"))
    implementation(project(":simtrace"))
    implementation("com.squareup:kotlinpoet:2.0.0")
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
