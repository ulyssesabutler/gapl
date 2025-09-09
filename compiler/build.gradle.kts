plugins {
    kotlin("jvm") version "2.0.21"
    application
}

application {
    mainClass.set("com.uabutler.GAPLKt")
    applicationName = "gapl"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":antlr"))
    testImplementation(kotlin("test"))
    implementation("com.strumenta:antlr-kotlin-runtime:1.0.0")
    implementation("org.yaml:snakeyaml:2.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    jvmArgs = jvmArgs?.plus("-ea") ?: listOf("-ea")
}

kotlin {
    jvmToolchain(8)
}
