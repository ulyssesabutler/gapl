package com.uabutler.lsp

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class GaplLanguageServer : LanguageServer, LanguageClientAware {

    private val textDocumentService = GaplTextDocumentService()
    private val workspaceService = GaplWorkspaceService()

    override fun connect(client: LanguageClient) {
        textDocumentService.client = client
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val capabilities = ServerCapabilities().apply {
            setTextDocumentSync(TextDocumentSyncKind.Full)
            setDefinitionProvider(true)
        }

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun shutdown(): CompletableFuture<Any> = CompletableFuture.completedFuture(null)

    override fun exit() {}

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService
}
