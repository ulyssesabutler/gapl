plugins {
    kotlin("jvm") version "2.0.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":antlr"))
    implementation("com.strumenta:antlr-kotlin-runtime:1.0.0")
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
