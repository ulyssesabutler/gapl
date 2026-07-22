package com.uabutler.lsp

import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

// The client is only known once GaplLanguageServer.connect is called, which LSP4J invokes after
// building the endpoint delegate graph (which itself calls getTextDocumentService() eagerly) -
// so it can't be a constructor parameter here.
class GaplTextDocumentService : TextDocumentService {

    lateinit var client: LanguageClient

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val document = params.textDocument
        AnalysisCoordinator.analyzeAndPublish(client, document.uri, document.text)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        // textDocumentSync is Full, so the single content change is always the whole document.
        val text = params.contentChanges.first().text
        AnalysisCoordinator.analyzeAndPublish(client, params.textDocument.uri, text)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        AnalysisCoordinator.forget(params.textDocument.uri)
        client.publishDiagnostics(PublishDiagnosticsParams(params.textDocument.uri, emptyList()))
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // Diagnostics are already kept live via didChange.
    }

    override fun definition(
        params: DefinitionParams,
    ): CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>> {
        val uri = params.textDocument.uri
        val declarationSpan = AnalysisCoordinator.definitionAt(uri, params.position)

        val locations = if (declarationSpan == null) emptyList() else listOf(Location(uri, declarationSpan.toLspRange()))
        return CompletableFuture.completedFuture(Either.forLeft(locations))
    }
}
