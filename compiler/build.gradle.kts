import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.rpm.Rpm

plugins {
    kotlin("jvm") version "2.0.21"
    application
    id("com.netflix.nebula.ospackage") version "12.1.1"
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

    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString()
        )
    }
}

tasks.named<JavaExec>("run") {
    jvmArgs = jvmArgs?.plus("-ea") ?: listOf("-ea")
}

ospackage {
    packageName = "gapl"
    summary = "GAPL compiler CLI"
    packageDescription = "Compiles GAPL source files to Verilog."
    maintainer = "Ulysses gapl@uabutler.com"
}

tasks.named<Deb>("buildDeb") {
    dependsOn("installDist")

    from(layout.buildDirectory.dir("install/gapl")) { into("/usr/lib/gapl") }
    link("/usr/bin/gapl", "/usr/lib/gapl/bin/gapl")

    requires("default-jre-headless | openjdk-17-jre-headless | java-runtime-headless")

    destinationDirectory.set(layout.buildDirectory.dir("pkg"))
    archiveBaseName.set("gapl")
    archiveVersion.set(project.version.toString().ifBlank { "dev" })
}

tasks.named<Rpm>("buildRpm") {
    dependsOn("installDist")

    os = org.redline_rpm.header.Os.LINUX
    release = "1"

    from(layout.buildDirectory.dir("install/gapl")) { into("/usr/lib/gapl") }
    link("/usr/bin/gapl", "/usr/lib/gapl/bin/gapl")

    requires("/usr/bin/java")

    destinationDirectory.set(layout.buildDirectory.dir("pkg"))
    archiveBaseName.set("gapl")
    archiveVersion.set(project.version.toString().ifBlank { "dev" })
}

kotlin {
    jvmToolchain(17)
}
