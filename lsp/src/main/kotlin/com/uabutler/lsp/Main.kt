package com.uabutler.lsp

import org.eclipse.lsp4j.launch.LSPLauncher

fun main() {
    // stdout is the JSON-RPC wire, framed with Content-Length headers - anything else that
    // writes to it (Logger's plain println, most notably) corrupts the protocol stream. Grab
    // the real stdout for the launcher first, then redirect System.out globally so nothing
    // downstream can pollute it, intentionally or not.
    val protocolOut = System.out
    System.setOut(System.err)

    val server = GaplLanguageServer()
    val launcher = LSPLauncher.createServerLauncher(server, System.`in`, protocolOut)
    server.connect(launcher.remoteProxy)
    launcher.startListening()
}
