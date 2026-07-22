package com.uabutler.lsp

import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.ColorInformation
import org.eclipse.lsp4j.ColorPresentation
import org.eclipse.lsp4j.ColorPresentationParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DeclarationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentColorParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.InlineValue
import org.eclipse.lsp4j.InlineValueParams
import org.eclipse.lsp4j.LinkedEditingRangeParams
import org.eclipse.lsp4j.LinkedEditingRanges
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Moniker
import org.eclipse.lsp4j.MonikerParams
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensDelta
import org.eclipse.lsp4j.SemanticTokensDeltaParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SemanticTokensRangeParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
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
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        val uri = params.textDocument.uri
        val declarationSpan = AnalysisCoordinator.definitionAt(uri, params.position)

        val locations = if (declarationSpan == null) emptyList() else listOf(Location(uri, declarationSpan.toLspRange()))
        return CompletableFuture.completedFuture(Either.forLeft(locations))
    }

    // Everything below is deliberately unimplemented (we don't advertise any of these
    // capabilities in initialize()), but LSP4J's TextDocumentService defaults for nearly every
    // optional method throw UnsupportedOperationException rather than returning gracefully.
    // Empirically, IntelliJ's LSP client calls a large fraction of these on document open
    // regardless of what's advertised, so leaving any of them at the default produces a JSON-RPC
    // "Internal error" response and a SEVERE log line - harmless, but noisy, and easy to trip
    // over one at a time. Overriding the full reachable set here once, rather than reactively.
    // ("Resolve"-style methods - resolveCompletionItem, resolveCodeAction, resolveCodeLens,
    // documentLinkResolve, resolveInlayHint, typeHierarchySupertypes/Subtypes,
    // callHierarchyIncoming/OutgoingCalls - are structurally unreachable: they only fire on an
    // item returned by another call we already make empty, so there's nothing to resolve. Not
    // overridden for that reason, not because they were missed. Same for the pull-diagnostics
    // textDocument/diagnostic endpoint, which callHierarchy/typeHierarchy-style clients only
    // invoke when a server explicitly advertises diagnosticProvider.)

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> =
        CompletableFuture.completedFuture(Either.forLeft(emptyList()))

    override fun hover(params: HoverParams): CompletableFuture<Hover?> =
        CompletableFuture.completedFuture(null)

    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp?> =
        CompletableFuture.completedFuture(null)

    override fun declaration(
        params: DeclarationParams,
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        CompletableFuture.completedFuture(Either.forLeft(emptyList()))

    override fun typeDefinition(
        params: TypeDefinitionParams,
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        CompletableFuture.completedFuture(Either.forLeft(emptyList()))

    override fun implementation(
        params: ImplementationParams,
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        CompletableFuture.completedFuture(Either.forLeft(emptyList()))

    override fun references(params: ReferenceParams): CompletableFuture<List<Location>> =
        CompletableFuture.completedFuture(emptyList())

    override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> =
        CompletableFuture.completedFuture(emptyList())

    override fun documentSymbol(
        params: DocumentSymbolParams,
    ): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> =
        CompletableFuture.completedFuture(emptyList())

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> =
        CompletableFuture.completedFuture(emptyList())

    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> =
        CompletableFuture.completedFuture(emptyList())

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> =
        CompletableFuture.completedFuture(emptyList())

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> =
        CompletableFuture.completedFuture(emptyList())

    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> =
        CompletableFuture.completedFuture(emptyList())

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit?> =
        CompletableFuture.completedFuture(null)

    override fun linkedEditingRange(params: LinkedEditingRangeParams): CompletableFuture<LinkedEditingRanges?> =
        CompletableFuture.completedFuture(null)

    override fun documentLink(params: DocumentLinkParams): CompletableFuture<List<DocumentLink>> =
        CompletableFuture.completedFuture(emptyList())

    override fun documentColor(params: DocumentColorParams): CompletableFuture<List<ColorInformation>> =
        CompletableFuture.completedFuture(emptyList())

    override fun colorPresentation(params: ColorPresentationParams): CompletableFuture<List<ColorPresentation>> =
        CompletableFuture.completedFuture(emptyList())

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> =
        CompletableFuture.completedFuture(emptyList())

    override fun prepareRename(
        params: PrepareRenameParams,
    ): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>?> =
        CompletableFuture.completedFuture(null)

    override fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): CompletableFuture<List<TypeHierarchyItem>> =
        CompletableFuture.completedFuture(emptyList())

    override fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): CompletableFuture<List<TypeHierarchyItem>> =
        CompletableFuture.completedFuture(emptyList())

    override fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): CompletableFuture<List<TypeHierarchyItem>> =
        CompletableFuture.completedFuture(emptyList())

    override fun prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture<List<CallHierarchyItem>> =
        CompletableFuture.completedFuture(emptyList())

    override fun callHierarchyIncomingCalls(params: CallHierarchyIncomingCallsParams): CompletableFuture<List<CallHierarchyIncomingCall>> =
        CompletableFuture.completedFuture(emptyList())

    override fun callHierarchyOutgoingCalls(params: CallHierarchyOutgoingCallsParams): CompletableFuture<List<CallHierarchyOutgoingCall>> =
        CompletableFuture.completedFuture(emptyList())

    override fun selectionRange(params: SelectionRangeParams): CompletableFuture<List<SelectionRange>> =
        CompletableFuture.completedFuture(emptyList())

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens?> {
        val data = AnalysisCoordinator.semanticTokensFor(params.textDocument.uri).encodeSemanticTokens()
        return CompletableFuture.completedFuture(SemanticTokens(data))
    }

    override fun semanticTokensFullDelta(
        params: SemanticTokensDeltaParams,
    ): CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>?> =
        CompletableFuture.completedFuture(null)

    override fun semanticTokensRange(params: SemanticTokensRangeParams): CompletableFuture<SemanticTokens?> =
        CompletableFuture.completedFuture(null)

    override fun moniker(params: MonikerParams): CompletableFuture<List<Moniker>> =
        CompletableFuture.completedFuture(emptyList())

    override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> =
        CompletableFuture.completedFuture(emptyList())

    override fun inlineValue(params: InlineValueParams): CompletableFuture<List<InlineValue>> =
        CompletableFuture.completedFuture(emptyList())
}
