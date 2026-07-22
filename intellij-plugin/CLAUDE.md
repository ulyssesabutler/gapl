# CLAUDE.md

This file provides guidance to Claude Code when working in this directory.

## What this is

A minimal IntelliJ-family plugin that provides editor support for GAPL (`.gapl` files) by
spawning and talking to the `gapl-lsp` language server — the same server `../vscode-extension`
talks to. This directory is Kotlin/JVM (via the `org.jetbrains.intellij.platform` Gradle plugin)
and is wired into the root Gradle build like any other subproject - `./gradlew build`/`clean` at
the repo root covers it automatically.

This plugin is intentionally a thin client, same rationale as `../vscode-extension`: all real
logic - parsing, diagnostics, netlist validation, go-to-definition - lives in `../lsp` and
`../analyzer`. If behavior looks wrong (a bad diagnostic, a wrong go-to-definition target), the
bug is almost certainly upstream, not here. Unlike the VSCode extension, this plugin doesn't even
need to register a Language/FileType or write any diagnostics/navigation logic itself - IntelliJ's
LSP Client API wires diagnostics, go-to-definition, hover, completion, find-usages, and rename
into native IDE UI automatically once the server advertises the corresponding capability. This
directory should only ever need changes for: server-spawning/file-association logic, or syntax
highlighting (not implemented yet - see Future TODOs).

## Build & dev commands

- `./gradlew :intellij-plugin:build` (from the repo root) - compiles, packages, and depends on
  `:lsp:shadowJar`, so building this plugin always guarantees the server jar its bundling and
  dev-mode path resolution both need actually exists (same pattern as `vscode-extension`).
- `./gradlew :intellij-plugin:buildPlugin` - packages a distributable zip
  (`build/distributions/intellij-plugin-<version>.zip`) and, critically, actually boots a headless
  sandboxed IDE instance as part of `buildSearchableOptions` - this is the only way to catch
  plugin-loading errors (bad `<depends>` declarations, etc.) that `compileKotlin` alone won't
  surface. Always run this, not just `compileKotlin`, after touching `plugin.xml`.
- `./gradlew :intellij-plugin:runIde` - launches a real sandboxed IDE with this plugin installed,
  for actually trying it against a `.gapl` file. **Needs a GUI** - couldn't be run in the sandbox
  this plugin was originally built in, only confirmed working (server spawns correctly, no
  plugin-loading errors) once tried on a real machine. Depends on `:lsp:shadowJar` and passes the
  resulting jar's path via the `gapl.lsp.jar` system property, so the sandboxed IDE runs that
  directly (`java -jar ...`) instead of extracting the bundled-resource copy described below - no
  manual setup needed before running this task.
- The `intellijIdea("2024.3")` target in `build.gradle.kts` determines which IDE Platform version
  gets downloaded to build/test against. This is independent of the plugin's own `sinceBuild`
  compatibility declaration and independent of which Gradle version builds it - see Known gotchas.

## Architecture

- `build.gradle.kts` - pinned to IntelliJ Platform Gradle Plugin **2.11.0**, not the latest
  (2.18.1+). Don't casually bump this - see Known gotchas, it requires Gradle 9.0.0+ from 2.12.0
  onward, and this repo deliberately stays on the Gradle 8.x line for now.
- `src/main/resources/META-INF/plugin.xml` - plugin manifest. `<depends>com.intellij.modules.platform</depends>`
  only - do **not** add `<depends>com.intellij.modules.lsp</depends>` back (see Known gotchas, it
  doesn't exist and breaks plugin loading despite compiling fine). Registers
  `GaplLspServerSupportProvider` via the `platform.lsp.serverSupportProvider` extension point.
- `src/main/kotlin/com/uabutler/intellij/GaplLspServerSupportProvider.kt` - `LspServerSupportProvider.fileOpened`:
  starts the server descriptor below when a `.gapl` file is opened.
- `src/main/kotlin/com/uabutler/intellij/GaplLspServerDescriptor.kt` - `ProjectWideLspServerDescriptor`:
  `isSupportedFile` (extension check) and `createCommandLine()` (server path resolution). Resolves
  the server jar via `gapl.lsp.jar` system property (dev/testing override) or else extracts the
  bundled `server/gapl-lsp.jar` resource (see `copyServerJar` below) to a content-addressed cache
  file under `PathManager.getSystemPath()`, and resolves `java` via the IDE's own bundled JVM
  (`java.home`) before falling back to `PATH`.
- `build.gradle.kts`'s `copyServerJar` task copies `:lsp:shadowJar`'s output into
  `src/main/resources/server/gapl-lsp.jar` before `processResources`, so it rides along as a
  resource inside this plugin's own compiled jar (and therefore inside `buildPlugin`'s zip) - see
  Future TODOs. That generated file is gitignored (`.gitignore`'s
  `intellij-plugin/src/main/resources/server/` rule) - don't hand-edit or commit it.

## Known gotchas (learned the hard way this session)

- **`buildPlugin`/`buildSearchableOptions` can "succeed" while the plugin is actually broken.**
  Gradle reported `BUILD SUCCESSFUL` even while the sandboxed IDE's log contained a SEVERE
  "Problems found loading plugins" error (a bad `<depends>` value). The Gradle task doesn't hard-fail
  on that - you have to actually read the log output, or grep it for `SEVERE`/`Problems found
  loading plugins`, to catch plugin-loading errors. Compiling cleanly proves nothing about whether
  `plugin.xml` is correct.
- **`<depends>com.intellij.modules.lsp</depends>` doesn't exist and will break plugin loading**,
  even though `com.intellij.platform.lsp.api.LspServerSupportProvider`/`ProjectWideLspServerDescriptor`
  compile and resolve fine without it (they're part of the core platform module,
  `com.intellij.modules.platform`, not a separate optional module you need to declare). This
  contradicted the official LSP docs at the time this was written, which suggested that dependency
  was required - possibly accurate for the newer `LspIntegrationProvider` API this plugin
  deliberately doesn't use, not the older `LspServerSupportProvider` this does. If you ever migrate
  to the newer API, re-check whether that dependency is actually needed there.
- **The IntelliJ Platform Gradle Plugin's minimum Gradle requirement is stricter than "any 8.x":**
  2.11.0 needs Gradle **8.13+** specifically (this repo bumped from 8.10 to 8.14.5, the final 8.x
  release, to satisfy this - see the root repo's history around when this plugin was added). 2.12.0+
  needs Gradle 9.0.0+, which is why this plugin is pinned below that.
- **The Gradle-plugin-tooling-version (2.11.0) and the IDE-Platform-API-version you target
  (`intellijIdea("2024.3")`) are independent axes.** Bumping/pinning one doesn't restrict the
  other - 2.11.0's tooling can still target a reasonably current IDE Platform release; what matters
  for LSP feature availability is the `intellijIdea(...)` version and IDE Platform API surface, not
  the build-tool version.
- **Bumping Gradle even within the 8.x line is not risk-free for this repo**, because
  `com.strumenta.antlr-kotlin` (the ANTLR plugin `../antlr` depends on, which everything else
  depends on transitively) used an internal Gradle worker API that was removed between 8.10 and
  8.14.5, breaking the *entire* build, not just this plugin, until it was bumped from 1.0.0 to
  1.0.3. If a future Gradle bump breaks things again, check `../antlr/build.gradle.kts` first.
- **A working `runIde` session can still log SEVERE "Internal error" responses that look alarming
  but aren't - or were, and now are fixed.** IntelliJ's LSP client proactively probes several
  optional capabilities (hover, documentSymbol, foldingRange, codeLens, inlayHint) on file open for
  editor decoration, regardless of what `initialize()` advertises. LSP4J's `TextDocumentService`
  default implementations for these `throw UnsupportedOperationException` rather than returning
  gracefully, which used to surface as exactly this error, once per capability, on every file open.
  Fixed server-side in `../lsp/src/main/kotlin/com/uabutler/lsp/GaplTextDocumentService.kt` (all
  five now return empty/null results explicitly) - if you see this pattern again for a *different*
  method name in the log, that method needs the same treatment added there, not here.

## Future TODOs

- **Server distribution - done.** `:lsp:shadowJar` (Gradle Shadow plugin, pinned to
  `com.gradleup.shadow` 8.3.6) produces a self-contained `gapl-lsp.jar`; `copyServerJar` bundles it
  as a plugin resource; `GaplLspServerDescriptor.kt` extracts it to a stable cache location and
  runs it with the IDE's own bundled JVM. Unlike `vscode-extension` (which still needs a friend to
  have a JDK on `PATH`), this plugin is genuinely zero-setup - the IDE always ships its own JVM,
  so `buildPlugin`'s zip works standalone with nothing else installed. Verified end-to-end: built
  `buildPlugin`, confirmed no plugin-loading errors in the log, unzipped the distribution to
  confirm `server/gapl-lsp.jar` survives inside the packaged plugin jar.
- **Syntax highlighting - done, server-side.** `../lsp` now implements `textDocument/semanticTokens/full`
  (keywords, operators, numbers, comments, and identifiers classified by resolved kind - function,
  interface, parameter, variable, type parameter). Per JetBrains' docs this plugin needs zero code for it - the
  platform's LSP client picks up semantic tokens automatically once the server advertises the
  capability, verified server-side via a protocol test and a manual run against a real `.gapl` file.
  Not yet visually confirmed rendering correctly in a real running IDE (no GUI in the sandbox this
  was built in) - worth a look next time `runIde` is used. If it's missing/wrong, the bug is almost
  certainly in `../lsp` or `../analyzer`'s `SemanticTokensCollector`, not here.
- **Packaging/publishing.** No JetBrains Marketplace publishing set up - `buildPlugin`'s zip output
  (`build/distributions/intellij-plugin-<version>.zip`) is manual-install only today (Settings ->
  Plugins -> gear icon -> "Install Plugin from Disk..."), which is exactly what's needed for
  handing this to someone for review without going through the Marketplace.
- **Gradle 9 / newer plugin tooling.** A deliberate follow-up if wanted, not something to bundle
  into unrelated work - see the gotchas above for what broke last time and needs re-checking
  (`antlr-kotlin`, `com.netflix.nebula.ospackage` in `../compiler`, `com.github.node-gradle.node`
  in `../vscode-extension`).
- **No automated tests.** Verification so far is `compileKotlin` + `buildPlugin` (including reading
  its log for plugin-loading errors) headlessly; `runIde` needs a GUI and hasn't been run by
  whoever originally wrote this. If this plugin grows, consider the IntelliJ Platform's own test
  fixtures (`IdeaPlatformTestingExtension`/`runIdeForUiTests`, etc.).
