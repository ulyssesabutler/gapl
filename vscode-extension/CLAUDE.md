# CLAUDE.md

This file provides guidance to Claude Code when working in this directory.

## What this is

A minimal VSCode extension that provides editor support for GAPL (`.gapl` files) by spawning
and talking to the `gapl-lsp` language server — a separate Kotlin/JVM subproject (`../lsp`) in
this same repo. This directory is npm/TypeScript, but it *is* wired into the root Gradle build
(via the `com.github.node-gradle.node` plugin, included in `settings.gradle.kts`) so the whole
monorepo can be driven from one build system - see Build & dev commands. It's still only loosely
coupled to the rest of the repo at the code level: it doesn't build or depend on Kotlin code
directly, it just launches a server binary that Gradle produces elsewhere.

This extension is intentionally a thin client. All real logic — parsing, diagnostics, netlist
validation, go-to-definition — lives in `../lsp` and `../analyzer`. If you're debugging something
that seems like "wrong behavior" (a bad diagnostic, a wrong go-to-definition target), the bug is
almost certainly upstream in `../analyzer` or `../lsp`, not in this extension. This directory
should only ever need changes for: editor-integration concerns (activation, server spawning,
settings), syntax highlighting, or new LSP capabilities the server has started advertising that
the extension needs to opt into.

## Build & dev commands

- `./gradlew :vscode-extension:build` (from the repo root) — the normal way to build this now.
  Downloads a pinned Node/npm (20.18.1) into the project if needed (no system Node required),
  runs `npm install`, then `npm run compile` - and also depends on `:lsp:installDist`, so building
  the extension always guarantees the server binary its default `gapl.serverPath` points at
  actually exists. Also included automatically in a plain `./gradlew build`/`./gradlew clean` from
  the repo root, alongside every Kotlin subproject. `clean` here removes both `out/` and
  `node_modules/` (not just build output) - a full clean means the next build re-runs
  `npm install` from scratch, and does *not* clean `:lsp` (run `./gradlew clean` at the repo root
  for that).
- Direct npm commands still work if you're iterating quickly and don't want Gradle's overhead,
  but skip the `:lsp:installDist` guarantee above: `npm install`, `npm run compile` (type-check,
  emit `out/*.js`), `npm run watch`.
- **To actually run it**: open *this directory* (`vscode-extension/`) as the VSCode workspace
  root — not the whole `gapl` repo — then press F5. `.vscode/launch.json` is workspace-relative,
  so VSCode won't find it (and will prompt for a generic debugger instead) unless this folder is
  the open workspace. F5's pre-launch task (`.vscode/tasks.json`) runs
  `./gradlew :vscode-extension:assemble` from the repo root - i.e. the same guarantee as above,
  both the TypeScript and the language server are current - then launches an Extension Development
  Host window with this extension loaded. No separate manual `:lsp:installDist` step needed
  anymore before F5.

## Architecture

- `package.json` — extension manifest. Registers the `gapl` language (`.gapl` extension,
  `language-configuration.json`), the TextMate grammar, activation on `onLanguage:gapl`, and the
  `gapl.serverPath` setting.
- `src/extension.ts` — the entire extension. `activate()` resolves the server command
  (`gapl.serverPath` setting, or the dev-path fallback above) and starts a
  `vscode-languageclient` `LanguageClient` scoped to `.gapl` files over stdio. `deactivate()`
  stops it. There is no other logic here by design.
- `language-configuration.json` — comment syntax (`//`, `/* */`) and bracket/auto-close pairs.
- `syntaxes/gapl.tmLanguage.json` — TextMate grammar for syntax highlighting. Cross-checked
  against `../antlr/src/main/antlr/CST.g4` (the authoritative token list) when written, not
  against real rendered output in an editor — see Future TODOs.
- `.vscode/launch.json` / `.vscode/tasks.json` — Extension Host debug config. Unlike normal
  per-developer editor preferences, these **are** checked in (the repo's blanket `.vscode/`
  gitignore rule has an explicit exception for this directory) because F5 doesn't work at all
  without them.
- `tsconfig.json` — `module`/`moduleResolution` are both set to `node16`. This pairing is
  required (not optional) because `vscode-languageclient` publishes its `/node` subpath via
  `package.json` `exports`, which the older/default resolution mode can't follow; TypeScript also
  errors (`TS5110`) if `module` and `moduleResolution` disagree once either is set to `Node16`.
  Don't "simplify" this back to `"module": "commonjs"` alone without re-testing — that's the
  exact configuration that broke previously.

## Known gotchas (learned the hard way this session, worth remembering)

- **stdout is sacred on the server side.** The LSP server talks JSON-RPC over stdio with
  `Content-Length`-framed messages on stdout. Any stray `println`/logging on the server writing to
  stdout corrupts the protocol stream and shows up client-side as opaque errors like "Header must
  provide a Content-Length property" — it looks like a client/extension bug but isn't one. If you
  see connection errors like that again, check `../lsp/src/main/kotlin/com/uabutler/lsp/Main.kt`
  first (it redirects `System.out` to protect against exactly this) before assuming this
  extension's code is at fault.
- If F5 prompts for a debugger type instead of launching straight into an Extension Development
  Host, the workspace root is wrong (see Build & dev commands above) — it's not a sign anything is
  actually broken.

## Future TODOs

- **Server distribution — done.** `:lsp` now produces a self-contained fat jar via the Gradle
  Shadow plugin (`:lsp:shadowJar`, pinned to `com.gradleup.shadow` 8.3.6 — the latest stable
  release; the `9.0.0-beta*` line targets Gradle 9, which this repo doesn't use yet). The
  `copyServerJar` task in `build.gradle.kts` stages that jar at `server/gapl-lsp.jar` inside this
  extension before packaging. `resolveServerOptions` in `src/extension.ts` checks, in order: the
  `gapl.serverPath` setting (advanced override, runs a script/executable directly), the bundled
  `server/gapl-lsp.jar` (real installs, run via `java -jar`), then the sibling `../lsp` project's
  dev-mode shadow-jar build output (local development). A friend installing the `.vsix` still
  needs a JDK 17+ on `PATH` (or `JAVA_HOME`) — VSCode doesn't ship its own JVM the way IntelliJ
  does (see `intellij-plugin/CLAUDE.md`) — `activate()` now shows a clear error notification if
  spawning `java` fails with ENOENT, rather than failing silently.
- **Packaging/publishing — packaging done, publishing not started.** `npm run package` (or
  `./gradlew :vscode-extension:packageVsix`) runs `vsce package`, producing a `.vsix` for manual
  install (`code --install-extension <file>.vsix` or the Extensions view's "Install from VSIX...")
  — no marketplace involved. `.vscodeignore` excludes dev-only files; verified the packaged
  `.vsix` is lean (a few hundred KB of production `node_modules` plus the ~4MB server jar, not the
  multi-hundred-MB `.gradle/nodejs` download that landed in it on the first attempt before
  `.vscodeignore` excluded it) and that `vsce` automatically prunes `devDependencies` from
  `node_modules` on its own. `vsce publish`/Marketplace listing still not set up.
- **Syntax highlighting quality.** `../lsp` now implements `textDocument/semanticTokens/full`
  (keywords, operators, numbers, identifiers classified by resolved kind), which VSCode's built-in
  LSP client layers automatically on top of the TextMate grammar below — no extension-side code
  needed. This likely covers most of what the TextMate grammar alone couldn't (distinguishing a
  declared function name from a parameter, for instance). The TextMate grammar itself
  (`syntaxes/gapl.tmLanguage.json`) has since been visually verified against real GAPL source in a
  rendered editor and confirmed working. Semantic tokens now cover comments too (`../antlr/src/main/antlr/CST.g4`'s
  `LineComment`/`BlockComment` moved from `-> skip` to `-> channel(HIDDEN)` so they still reach
  `Lexer.allTokens`, classified in `../analyzer/src/main/kotlin/com/uabutler/Analyzer.kt`'s
  `lexicalTokens`/`commentTokens`) - VSCode was already covering comments via this TextMate grammar
  regardless, so this mainly mattered for IntelliJ (see `../intellij-plugin/CLAUDE.md`). Semantic
  tokens still don't cover accessors (`.`, `[i]`, `[a:b]`) - the TextMate grammar is still what's
  responsible for those.
- **New LSP capabilities land here too.** `../analyzer`/`../lsp` are expected to grow hover and
  find-references next (see `../lsp`'s own follow-up notes) — `vscode-languageclient` picks up
  most capabilities automatically via content negotiation, so this usually needs no changes, but
  double-check after each new capability lands that nothing here needs updating (e.g. explicit
  capability opt-ins, custom commands).
- **Go-to-definition gap.** Record-interface port access (`x.port`) isn't covered by go-to-definition
  yet — it resolves through member-access in the resolver, not the symbol table `DefinitionsCollector`
  captures from. This is an `../analyzer` limitation, not something to fix here, but worth knowing
  if a bug report says "go to definition doesn't work on struct fields."
- **No extension-level tests.** Verification so far has been manual (F5 + a real `.gapl` file).
  If this extension grows beyond "spawn the client and get out of the way," consider
  `@vscode/test-electron` for automated integration tests.
- **IntelliJ.** Done — see `../intellij-plugin`. Leans on IntelliJ's built-in first-party LSP
  client support (`LspServerSupportProvider`) rather than a bespoke PSI-based plugin or the
  community LSP4IJ plugin, so the same `gapl-lsp` server serves both editors — a second thin
  client directory analogous to this one, not a rewrite of `../lsp`.
