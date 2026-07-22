package com.uabutler.lsp

import org.eclipse.lsp4j.launch.LSPLauncher

fun main() {
    val server = GaplLanguageServer()
    val launcher = LSPLauncher.createServerLauncher(server, System.`in`, System.out)
    server.connect(launcher.remoteProxy)
    launcher.startListening()
}
