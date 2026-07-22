package com.uabutler.lsp

import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Finds the (line, character) LSP position of the nth (0-indexed) occurrence of `needle` in
// `text`, so tests can point at a specific identifier without hand-counting columns.
private fun positionOf(text: String, needle: String, occurrence: Int = 0): Position {
    var seen = 0
    text.lines().forEachIndexed { lineIndex, line ->
        var fromIndex = 0
        while (true) {
            val idx = line.indexOf(needle, fromIndex)
            if (idx == -1) break
            if (seen == occurrence) return Position(lineIndex, idx)
            seen++
            fromIndex = idx + 1
        }
    }
    error("'$needle' occurrence $occurrence not found in text")
}

// Client-side inverse of AnalysisCoordinator.encodeSemanticTokens, for asserting on decoded
// positions/types rather than hand-computing deltas in every test.
private data class DecodedSemanticToken(val line: Int, val startChar: Int, val length: Int, val tokenType: String)

private fun decodeSemanticTokens(data: List<Int>): List<DecodedSemanticToken> {
    val decoded = mutableListOf<DecodedSemanticToken>()
    var line = 0
    var startChar = 0

    var i = 0
    while (i < data.size) {
        val deltaLine = data[i]
        val deltaStartChar = data[i + 1]
        val length = data[i + 2]
        val tokenTypeIndex = data[i + 3]

        line += deltaLine
        startChar = if (deltaLine == 0) startChar + deltaStartChar else deltaStartChar

        decoded.add(DecodedSemanticToken(line, startChar, length, semanticTokenTypesLegend[tokenTypeIndex]))
        i += 5
    }

    return decoded
}

// A minimal recording LanguageClient - captures every publishDiagnostics call on a queue so
// tests can block until the notification actually arrives over the (async) piped connection.
private class RecordingLanguageClient : LanguageClient {
    val publishedDiagnostics = LinkedBlockingQueue<PublishDiagnosticsParams>()

    override fun telemetryEvent(obj: Any) {}
    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        publishedDiagnostics.put(diagnostics)
    }
    override fun showMessage(messageParams: MessageParams) {}
    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
        CompletableFuture()
    override fun logMessage(message: MessageParams) {}
}

// Wires a real GaplLanguageServer to a real LSP4J client over piped streams, so these tests
// exercise the actual JSON-RPC/protocol path rather than calling server methods directly.
class GaplLanguageServerProtocolTest {

    private fun nextDiagnostics(queue: LinkedBlockingQueue<PublishDiagnosticsParams>): PublishDiagnosticsParams =
        queue.poll(5, TimeUnit.SECONDS) ?: error("Timed out waiting for publishDiagnostics")

    private fun withServer(block: (LanguageServer, RecordingLanguageClient) -> Unit) {
        val serverIn = PipedInputStream()
        val clientOut = PipedOutputStream(serverIn)
        val clientIn = PipedInputStream()
        val serverOut = PipedOutputStream(clientIn)

        val server = GaplLanguageServer()
        val serverLauncher = LSPLauncher.createServerLauncher(server, serverIn, serverOut)
        server.connect(serverLauncher.remoteProxy)
        serverLauncher.startListening()

        val client = RecordingLanguageClient()
        val clientLauncher = LSPLauncher.createClientLauncher(client, clientIn, clientOut)
        clientLauncher.startListening()

        val serverProxy = clientLauncher.remoteProxy
        serverProxy.initialize(InitializeParams()).get(5, TimeUnit.SECONDS)

        block(serverProxy, client)
    }

    @Test
    fun `opening a document with a resolver error reports one diagnostic`() {
        withServer { server, client ->
            val gapl = """
                function top() i: wire => o: wire {
                    i => register(wire)[0] => o;
                }
            """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem("file:///test.gapl", "gapl", 1, gapl))
            )

            val published = nextDiagnostics(client.publishedDiagnostics)
            assertEquals("file:///test.gapl", published.uri)
            assertEquals(1, published.diagnostics.size)
            assertEquals(DiagnosticSeverity.Error, published.diagnostics.first().severity)
        }
    }

    @Test
    fun `opening a document with an undriven output port reports one diagnostic`() {
        // This is exactly the case that used to crash the compiler mid-transform instead of
        // reporting a diagnostic (see commit history) - Analyzer.analyze alone can't catch it,
        // since it only surfaces from ModuleBuilder (netlist-building), which analyzeFull adds.
        withServer { server, client ->
            val gapl = """
                function test() i: wire => o: wire {
                    i => declare x: wire;
                }
            """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem("file:///undriven.gapl", "gapl", 1, gapl))
            )

            val published = nextDiagnostics(client.publishedDiagnostics)
            assertEquals(1, published.diagnostics.size)
            assertEquals(DiagnosticSeverity.Error, published.diagnostics.first().severity)
        }
    }

    @Test
    fun `opening a document with no errors reports no diagnostics`() {
        withServer { server, client ->
            val gapl = """
                function top() i: wire => o: wire {
                    i => o;
                }
            """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem("file:///ok.gapl", "gapl", 1, gapl))
            )

            val published = nextDiagnostics(client.publishedDiagnostics)
            assertTrue(published.diagnostics.isEmpty())
        }
    }

    @Test
    fun `fixing the error on didChange clears the diagnostic`() {
        withServer { server, client ->
            val broken = """
                function top() i: wire => o: wire {
                    i => register(wire)[0] => o;
                }
            """.trimIndent()
            val fixed = """
                function top() i: wire => o: wire {
                    i => o;
                }
            """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem("file:///fix.gapl", "gapl", 1, broken))
            )
            nextDiagnostics(client.publishedDiagnostics)

            server.textDocumentService.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier("file:///fix.gapl", 2),
                    listOf(TextDocumentContentChangeEvent(fixed)),
                )
            )

            val published = nextDiagnostics(client.publishedDiagnostics)
            assertTrue(published.diagnostics.isEmpty())
        }
    }

    @Test
    fun `go to definition on a locally declared circuit node returns the declaration`() {
        withServer { server, client ->
            val gapl = """
                function test() i: wire => o: wire {
                    i => declare x: wire;
                    x => o;
                }
            """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem("file:///def.gapl", "gapl", 1, gapl))
            )
            nextDiagnostics(client.publishedDiagnostics)

            val usagePosition = positionOf(gapl, "x => o")
            val result = server.textDocumentService.definition(
                DefinitionParams(TextDocumentIdentifier("file:///def.gapl"), usagePosition)
            ).get(5, TimeUnit.SECONDS)

            val locations = result.left
            assertEquals(1, locations.size)
            assertEquals("file:///def.gapl", locations.first().uri)
            assertEquals(positionOf(gapl, "declare x").line, locations.first().range.start.line)
        }
    }

    @Test
    fun `go to definition on a builtin function reference returns nothing`() {
        withServer { server, client ->
            val gapl = """
                function top() i: wire => o: wire {
                    i => register(wire)[0] => o;
                }
            """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem("file:///builtin.gapl", "gapl", 1, gapl))
            )
            nextDiagnostics(client.publishedDiagnostics)

            val usagePosition = positionOf(gapl, "register")
            val result = server.textDocumentService.definition(
                DefinitionParams(TextDocumentIdentifier("file:///builtin.gapl"), usagePosition)
            ).get(5, TimeUnit.SECONDS)

            assertTrue(result.left.isEmpty())
        }
    }

    @Test
    fun `initialize advertises a semantic tokens legend`() {
        withServer { server, _ ->
            val result = server.initialize(InitializeParams()).get(5, TimeUnit.SECONDS)
            val legend = result.capabilities.semanticTokensProvider.legend
            assertTrue(legend.tokenTypes.containsAll(listOf("keyword", "function", "variable", "parameter")))
        }
    }

    @Test
    fun `semantic tokens classify a declared function, a keyword, and a declared variable`() {
        withServer { server, client ->
            // "top" is never referenced elsewhere in the file - this is exactly the case that
            // proves declarations are classified independently, not only riding along on usages.
            val gapl = """
                function top() i: wire => o: wire {
                    i => declare x: wire;
                    x => o;
                }
            """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem("file:///tokens.gapl", "gapl", 1, gapl))
            )
            nextDiagnostics(client.publishedDiagnostics)

            val tokens = server.textDocumentService.semanticTokensFull(
                SemanticTokensParams(TextDocumentIdentifier("file:///tokens.gapl"))
            ).get(5, TimeUnit.SECONDS)

            val decoded = decodeSemanticTokens(tokens.data)

            fun hasTokenAt(needle: String, occurrence: Int, tokenType: String): Boolean {
                val position = positionOf(gapl, needle, occurrence)
                return decoded.any { it.line == position.line && it.startChar == position.character && it.tokenType == tokenType }
            }

            assertTrue(hasTokenAt("top", 0, "function"))
            assertTrue(hasTokenAt("function", 0, "keyword"))
            assertTrue(hasTokenAt("x:", 0, "variable"))
            assertTrue(hasTokenAt("=>", 0, "operator"))
        }
    }
}
