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
  runs `npm install`, then `npm run compile`. Also included automatically in a plain
  `./gradlew build`/`./gradlew clean` from the repo root, alongside every Kotlin subproject.
  `clean` here removes both `out/` and `node_modules/` (not just build output) - a full clean
  means the next build re-runs `npm install` from scratch.
- Direct npm commands still work if you're iterating quickly and don't want Gradle's overhead:
  `npm install`, `npm run compile` (type-check, emit `out/*.js`), `npm run watch`.
- **To actually run it**: open *this directory* (`vscode-extension/`) as the VSCode workspace
  root — not the whole `gapl` repo — then press F5. `.vscode/launch.json` is workspace-relative,
  so VSCode won't find it (and will prompt for a generic debugger instead) unless this folder is
  the open workspace. F5 runs the `compile` task first (`.vscode/tasks.json`), then launches an
  Extension Development Host window with this extension loaded.
- Before F5 will do anything useful, the server itself needs to exist: run
  `./gradlew :lsp:installDist` from the repo root. The extension's default `gapl.serverPath`
  (when the setting is left empty) points at `../lsp/build/install/gapl-lsp/bin/gapl-lsp` relative
  to this extension — a dev-only convenience, not something a real install should rely on (see
  Future TODOs).

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

- **Server distribution.** `gapl.serverPath` currently has no good default for a real install —
  it either requires the user to build `../lsp` themselves via Gradle and set the path manually,
  or relies on a relative dev-path guess that only works if this extension and the repo are
  checked out side by side. Before this is usable by anyone other than someone hacking on the
  compiler itself, decide on and implement one of: bundling a prebuilt server JAR inside the
  `.vsix` (and shelling out to a bundled/system `java`), auto-downloading a release artifact on
  first activation, or requiring a separately-installed `gapl` CLI package (see the Debian/RPM
  packaging already set up in `../compiler/build.gradle.kts`) and locating it on `PATH`.
- **Packaging/publishing.** No `vsce package`/`vsce publish` setup yet — this only runs via the
  Extension Development Host today.
- **Syntax highlighting quality.** The TextMate grammar hasn't been visually verified against real
  GAPL source in a rendered editor (no GUI access when it was written). Do a pass with real
  `.gapl` files (e.g. `../gapl-example/src/example.gapl`) and extend it — accessors (`.`, `[i]`,
  `[a:b]`), function/interface declaration names as distinct scopes, generic parameter lists — are
  all currently un-highlighted or only weakly distinguished.
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
- **IntelliJ.** Not started. The plan discussed earlier in this project is to lean on IntelliJ's
  built-in generic LSP client support (or the community LSP4IJ plugin) rather than a bespoke
  PSI-based plugin, so the same `gapl-lsp` server would serve both editors — a second thin client
  directory analogous to this one, not a rewrite of `../lsp`.
