import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.rpm.Rpm

plugins {
    kotlin("jvm") version "2.0.21"
    application
    id("com.netflix.nebula.ospackage-application") version "12.1.1"
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
    packageDescription = "GAPL compiler (runs on system JRE)."
    maintainer = "Ulysses Butler gapl@uabutler.com"

    requires("/usr/bin/java")
}

tasks.named<Deb>("buildDeb") {
    dependsOn("installDist")
    link("/usr/bin/gapl", "/opt/gapl/bin/gapl")

    destinationDirectory.set(layout.buildDirectory.dir("pkg"))
    archiveBaseName.set("gapl")
    archiveVersion.set(project.version.toString().ifBlank { "0.0.1" })
}

tasks.named<Rpm>("buildRpm") {
    dependsOn("installDist")
    link("/usr/bin/gapl", "/opt/gapl/bin/gapl")

    destinationDirectory.set(layout.buildDirectory.dir("pkg"))
    archiveBaseName.set("gapl")
    archiveVersion.set(project.version.toString().ifBlank { "0.0.1" })
}

tasks.register("deb") {
    group = "distribution"
    dependsOn("buildDeb")
}

tasks.register("rpm") {
    group = "distribution"
    dependsOn("buildRpm")
}


kotlin {
    jvmToolchain(17)
}
