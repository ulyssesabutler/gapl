plugins {
    kotlin("jvm") version "2.0.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":analyzer"))
    implementation(project(":simengine"))
    implementation("com.squareup:kotlinpoet:2.0.0")
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
