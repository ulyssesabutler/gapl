package com.uabutler.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import java.io.File
import java.security.MessageDigest

// The IDE always ships its own JVM (JetBrains Runtime) - prefer that over requiring a separate
// system Java install, which the bundled-jar approach below would otherwise need. Falls back to
// a bare "java" resolved against PATH if java.home ever doesn't point at a real installation.
private fun javaExecutable(): String {
    val isWindows = System.getProperty("os.name").startsWith("Windows")
    val candidate = File(System.getProperty("java.home"), if (isWindows) "bin/java.exe" else "bin/java")
    return if (candidate.isFile) candidate.absolutePath else "java"
}

// gapl-lsp.jar is bundled into this plugin's own jar as a resource (see build.gradle.kts'
// copyServerJar task) so buildPlugin's zip is self-contained - no repo clone, no Gradle needed
// for a friend installing "from disk". It can't be `java -jar`'d straight out of the classpath
// though, so extract it once to a stable, content-addressed cache location and reuse it after
// that (re-extracting only if the bundled jar's content actually changes, e.g. after an update).
private fun extractedBundledServerJar(): File {
    val bytes = GaplLspServerDescriptor::class.java.getResourceAsStream("/server/gapl-lsp.jar")
        ?.use { it.readBytes() }
        ?: error("gapl-lsp.jar was not bundled as a plugin resource")
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)

    val cacheDir = File(PathManager.getSystemPath(), "gapl-lsp-server")
    cacheDir.mkdirs()
    val target = File(cacheDir, "gapl-lsp-$digest.jar")

    if (!target.exists()) {
        val tmp = File.createTempFile("gapl-lsp-", ".jar", cacheDir)
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            // Lost a race with another IDE window extracting the same content concurrently -
            // target exists either way, so just clean up our temp file.
            tmp.delete()
        }
    }

    return target
}

// Dev/testing override (set by the runIde Gradle task) points straight at :lsp's shadow-jar
// build output, bypassing the bundled-resource extraction above entirely.
private fun resolveServerJar(): String =
    System.getProperty("gapl.lsp.jar") ?: extractedBundledServerJar().absolutePath

class GaplLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "GAPL") {

    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "gapl"

    override fun createCommandLine(): GeneralCommandLine =
        GeneralCommandLine(javaExecutable(), "-jar", resolveServerJar())
}
