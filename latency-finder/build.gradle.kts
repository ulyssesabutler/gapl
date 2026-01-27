plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Keep it empty for now. Add stuff when you inevitably can't resist.
    testImplementation(kotlin("test"))
}

application {
    // Kotlin top-level main in Main.kt compiles to MainKt
    mainClass.set("MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
