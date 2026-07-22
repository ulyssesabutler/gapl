package com.uabutler.lsp

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.services.WorkspaceService

class GaplWorkspaceService : WorkspaceService {

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        // No configurable settings yet.
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        // No workspace-level file watching yet - each document is analyzed independently.
    }
}
