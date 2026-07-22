package com.uabutler.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor

// Server path resolution is deliberately minimal for now, matching the same open item as
// vscode-extension's gapl.serverPath: no bundling, no settings UI yet. Checks a system property
// (settable for dev/testing, e.g. via the runIde task) before falling back to a bare command name
// resolved against PATH, the same assumption a real packaged install would eventually rely on.
private fun resolveServerCommand(): String =
    System.getProperty("gapl.lsp.path") ?: "gapl-lsp"

class GaplLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "GAPL") {

    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "gapl"

    override fun createCommandLine(): GeneralCommandLine = GeneralCommandLine(resolveServerCommand())
}
