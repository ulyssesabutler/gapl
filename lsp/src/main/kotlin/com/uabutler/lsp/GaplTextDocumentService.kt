package com.uabutler.lsp

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService

// The client is only known once GaplLanguageServer.connect is called, which LSP4J invokes after
// building the endpoint delegate graph (which itself calls getTextDocumentService() eagerly) -
// so it can't be a constructor parameter here.
class GaplTextDocumentService : TextDocumentService {

    lateinit var client: LanguageClient

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val document = params.textDocument
        DiagnosticsConverter.analyzeAndPublish(client, document.uri, document.text)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        // textDocumentSync is Full, so the single content change is always the whole document.
        val text = params.contentChanges.first().text
        DiagnosticsConverter.analyzeAndPublish(client, params.textDocument.uri, text)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        client.publishDiagnostics(PublishDiagnosticsParams(params.textDocument.uri, emptyList()))
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // Diagnostics are already kept live via didChange.
    }
}
