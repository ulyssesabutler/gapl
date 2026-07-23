# CLAUDE.md

This file provides guidance to Claude Code when working in this directory.

## What this is

The shared core of the GAPL compiler: parses GAPL source, resolves names/types, and builds a netlist
intermediate representation (IR) — the dataflow graph GAPL functions compile down to. Everything else
in the repo builds on this: `../compiler` turns the netlist IR into Verilog, `../lsp` wraps this
module directly for editor diagnostics/go-to-definition/semantic-tokens. See the root `../CLAUDE.md`
for the overall module graph.

`analyzer` is deliberately dependency-light (`analyzer/build.gradle.kts` only pulls in `:antlr` and
the antlr-kotlin runtime — no OR-Tools, no snakeyaml, no protobuf) since it's shared by both
`compiler` (which needs those, for retiming) and `lsp` (which doesn't want that weight in every editor
plugin's bundled server jar). If something needs a `compiler`-only concept (the real Verilog AST, the
retiming solver, OR-Tools), it belongs in `compiler`, not here — `compiler` depends on `analyzer`,
never the reverse.

Directory structure changed recently (commit `be5a026`, "Refactored analyzer and compiler directory
structures") — don't trust a stale mental model of where things live; the paths below are current as
of that commit.

## The pipeline: `Analyzer.kt`

The single entry point, an `object` with two orchestration functions:

- **`analyze(gapl, options)`** — preprocess (see stdlib trick below) → `Parser.fromString(...).program()`
  → `Resolver.analyze(program)` → merge in a second lex pass for keywords/operators/numbers/comments
  (`lexicalTokens`/`commentTokens` — these never make it into the AST, so semantic-token classification
  for them needs its own pass) → shift all spans back to user-source coordinates. Parse + resolve
  only, **no netlist building**. Cheap, and what an LSP wants for live-as-you-type feedback.
- **`analyzeFull(gapl, options)`** — calls `analyze()` first; if there are already diagnostics, returns
  immediately with `modules = null` (deliberately — `ModuleBuilder` assumes a well-formed resolved AST
  and can crash on a structurally incomplete one); otherwise runs
  `ModuleBuilder(analysis.ast, stdLibLineOffset(options)).buildAllModules()` and merges its
  diagnostics in too. What `compiler` needs before generating Verilog, and what any test asserting on
  `BuilderDiagnosticKind` diagnostics (undriven wires, width mismatches, combinational loops) must call
  — see `AnalyzerDiagnosticsTestUtil.compileFullExpectingDiagnostics` in Testing below.

**The stdlib-prepended-source trick**: `Options.includeStdLib` (default `true`) makes `preprocess()`
textually prepend the entire standard library source (`util/StandardLibrary.kt`'s `standardLibary`
string — note the misspelling is in the actual identifier, grep accordingly) to the user's source and
parse the whole thing as one program — no separate stdlib-loading mechanism exists. Every span computed
downstream is therefore in *preprocessed* coordinates until explicitly shifted back by
`Analyzer.shiftToUserSource`/`shiftDefinitionsToUserSource`/`shiftSemanticTokensToUserSource` (only
called from `analyze`/`analyzeFull` — a caller that invokes `Parser`/`Resolver`/`ModuleBuilder`
directly with stdlib included will get spans that don't match the original file). A diagnostic whose
span still lands inside the prepended region after shifting is kept but flagged as likely a compiler
bug (no user code to blame); definitions and semantic tokens in that region are silently dropped
instead, since there's no real file for an editor to point at.

## Parsing → Resolution → Netlist building

Three distinct stages, each producing a different tree:

1. **`Parser.kt`** wraps the ANTLR-Kotlin generated lexer/parser (from `:antlr`, built from
   `antlr/src/main/antlr/CST.g4`) and produces **raw ANTLR parse-tree context objects**, no AST of its
   own. Every parse method is wrapped in `guarded { ... }`, which throws `DiagnosticsException`
   immediately if any syntax errors were collected — the resolver must never run on a tree ANTLR's
   error-recovery synthesized from invalid input.
2. **`resolver/`** walks that raw parse tree and produces the actual semantic AST (`ast/node/`,
   rooted at `ProgramNode` — plain `data class`es implementing `GAPLNode { val span: SourceSpan }`,
   roughly mirroring the grammar). This is also where name resolution, symbol validation, go-to-definition
   linking, and semantic-token classification for identifiers all happen, as side effects of walking
   the tree.
3. **`netlistir/builder/`** walks the semantic AST and produces the netlist IR (`netlistir/netlist/`)
   — the actual dataflow graph.

### Resolution: the `Scope` pattern

`resolver/scope/Scope.kt` is an interface with one real owner: `ProgramScope` (the root,
`parentScope = null`) is the only class that actually constructs `DiagnosticsCollector`/
`DefinitionsCollector`/`SemanticTokensCollector` instances. Every other `Scope` gets them "for free"
via default-interface-getter delegation to `parentScope!!` — the pattern used consistently for all
three collectors. `Scope.resolve(identifier)` is the shared choke point that records both a
`DefinitionLink` and a semantic token as a side effect of resolving any identifier.

Concrete `*Scope` subclasses use one of two patterns: **interface delegation**
(`class Foo(parentScope: Scope, ...) : Scope by parentScope`) when a scope introduces no new locally-scoped
declarations, or **explicit override** (`override val parentScope`, custom `resolveLocal`/`symbols`)
when it does. `resolver/scope/functions/circuits/CircuitExpressionScope.kt` is the largest file here
(handles every `CircuitExpressionNode` variant).

### Netlist building

`netlistir/netlist/` holds the IR types, all reference-identity (not structural-equality) by design —
see gotcha below:
- `Node` (sealed): `VirtualNode` (→ `VirtualIONode`/`VirtualBodyNode`, synthetic, never produced here,
  only by graph-conversion machinery), `IONode` (→ `InputNode`/`OutputNode`), `BodyNode` (→
  `ModuleInvocationNode`, `PredefinedFunctionNode`, `PassThroughNode`).
- `Wire`/`WireVector`/`WireVectorGroup` — a GAPL interface (record/vector/wire, possibly nested)
  flattens down to a list of `WireVector`s, each holding indexed `Wire`s. `WireVector.Projection`
  handles slicing (`i[0:size-1]`).
- `Module`/`MutableModule` — `Module.Invocation(gaplFunctionName, interfaces, parameters)` is the
  identity key for one concrete instantiation of a (possibly generic) function, used as a map key
  throughout. `MutableModule.connect()` throws if an input wire already has a driver (each input can
  only ever have one).

`netlistir/builder/ModuleBuilder.kt` orchestrates building every module in a program (two-phase: build
concrete functions first, then loop building whatever's newly-referenced-but-unbuilt until nothing new
appears, via `ModuleInstantiationTracker`'s `Module.Invocation`-keyed memoization). `NodeBuilder.kt`
does the actual per-function-instantiation node/wire construction, tracking `nodeSpans` separately
from the IR (so diagnostics get precise declaration spans without the IR itself carrying
source-location baggage that would go stale once transformers relocate nodes). After all modules build
diagnostic-free, `ModuleBuilder` runs the whole-program combinational-loop check — see below.

## Generic graph utilities (`util/graph/`) and the combinational-loop check

`util/graph/{Graph,WeightedGraph,LeisersonCircuitGraph,HierarchicalLeisersonCircuitGraph}.kt` are
fully generic (netlist-agnostic) graph algorithms, moved here from `compiler` specifically so an
analyzer-side combinational-loop diagnostic could reuse the exact same graph machinery `compiler`'s
retiming pass builds on — without pulling OR-Tools/snakeyaml into `analyzer` (those stayed with the
retiming *solvers* in `compiler`, which are genuinely netlist-coupled and OR-Tools-dependent; the
generic graph representation and cycle-detection logic aren't).

- `Graph.kt` — base type; `topologicalSort()` (Kahn's, throws on a cycle), `stronglyConnectedComponentsTarjan()`
  (hand-written **iterative**, not recursive — a real GAPL netlist can be thousands of nodes deep, and
  a naive recursive DFS would blow the JVM stack once something runs it unconditionally on every
  compile rather than only on retiming-opted-in designs), `subgraph()`, `condenseToDag()`.
- `LeisersonCircuitGraph.kt` — adds the "retiming-ready" invariant: its constructor validates the
  zero-weight-edge subgraph has no cycle, throwing `IllegalArgumentException` if it does. This is why
  `HierarchicalLeisersonCircuitGraph.flatten()` (which returns one of these) crashes on a cyclic
  design — use `flattenToWeightedGraph()` instead (returns a plain, unvalidated `WeightedGraph`) if you
  need to inspect *which* nodes are in a cycle rather than treat one as unrecoverable.
- `HierarchicalLeisersonCircuitGraph.kt` — a call site becomes a `ChildGraphNode` wrapping the callee's
  own nested graph, so `flatten()`/`flattenToWeightedGraph()` can recursively inline an entire call
  tree into one flat graph. Takes explicit `rootAttachment`/`leafAttachment` nodes at construction
  (not inferred from degree) — a function can have more than one graph-theoretic leaf (e.g. a
  legitimately-unused generated value), which used to make degree-based inference pick the wrong one.

`netlistir/util/graph/{NetlistLeisersonCircuitConverter,HierarchicalNetlistLeisersonCircuitConverter}.kt`
convert between the netlist IR and this graph representation — register nodes get walked through to
compute per-edge register-hop weight, non-register connections become graph edges.

`netlistir/builder/util/CombinationalLoopDetector.kt` (`findCombinationalLoops`, called from
`ModuleBuilder.buildAllModules` after a clean build) is the payoff: builds hierarchical graphs for
every module, restricts to **root modules only** (nothing else calls them — flattening a root's graph
already recursively inlines its whole reachable call tree, so this is both complete and
non-duplicative across call-nesting levels), takes the zero-weight subgraph of the flattened result,
and runs Tarjan's SCC on it. Loop nodes are ranked deterministically (real name over
`AnonymousIdentifierGenerator`-synthesized name; a declared `BodyNode` over a function's own
`IONode` parameter, since parameter names are inherently generic — reused identically on every call;
alphabetical as a final tie-break) specifically so the same textual bug rediscovered through different
generic instantiations produces byte-identical diagnostics and collapses via `DiagnosticsCollector`'s
dedup (below) instead of showing up once per instantiation.

## Diagnostics system (`diagnostics/`)

`Diagnostic(severity, span, kind: DiagnosticKind, note)`. `DiagnosticKind` is a sealed interface;
concrete families are `SyntaxDiagnosticKind` (parser), `ResolverDiagnosticKind`, `BuilderDiagnosticKind`
(netlist builder — includes `CombinationalLoop`) — every concrete kind is a `data class`, meaning real
structural equality, which matters directly below.

**`DiagnosticsCollector` dedups exact repeats.** Its backing store is a `LinkedHashSet<Diagnostic>`,
not a `List` — a diagnostic that's identical in severity, span, and full kind content to one already
reported is silently dropped, preserving order for everything else. This is load-bearing for the
combinational-loop check: the same textual bug can be legitimately rediscovered once per otherwise-unrelated
generic instantiation (e.g. a buggy helper called with 10 different unrelated parameter values), and
this is what collapses those into one diagnostic instead of ten. Two genuinely different diagnostics
never collide by accident, since their `kind`'s own fields (port names, node names, etc.) always
differ — but this does mean any new `DiagnosticKind` should stay a plain data class with real
structural equality, and that node-ranking anywhere feeding a diagnostic's content needs to stay fully
deterministic (not dependent on `Set`/`Map` iteration order) for the dedup to actually catch true
duplicates.

`SourceSpan(startLine, startColumn, endLine, endColumn)` — lines 1-indexed, `of(token)`/`of(context)`
factories, `shiftedLines(delta)`. `DiagnosticFormatter` renders a diagnostic against source as a
`message\nN | <line>\n    ^` snippet. `ParserErrorListener` is the ANTLR `BaseErrorListener` that
feeds syntax errors into a `DiagnosticsCollector`.

`ModuleBuilder` skips the combinational-loop check entirely if *any* earlier diagnostic already
exists (not just resolver diagnostics — including its own from failed instantiations), since the
check's graph-flattening resolves wire connections eagerly and will throw/NPE on a structurally
incomplete netlist, which is expected whenever something upstream already failed. Don't "fix" this to
always run — it would crash instead of gracefully surfacing the more fundamental earlier error.

## Semantic tokens

`resolver/scope/SemanticTokensCollector.kt` holds `SemanticTokenKind` (KEYWORD/OPERATOR/NUMBER/
FUNCTION/INTERFACE/PARAMETER/VARIABLE/TYPE_PARAMETER/COMMENT). Identifiers get classified as a side
effect of `Scope.resolve()` plus separate declaration-site recording; keywords/operators/numbers/comments
come from `Analyzer.kt`'s independent re-lex pass, since none of those appear in the AST. Comments are
on ANTLR's hidden channel in `CST.g4` (`-> channel(HIDDEN)`, not `-> skip`) specifically so this re-lex
pass still sees them — both editor clients depend on this (see `../vscode-extension/CLAUDE.md`). Not
elaborated further here — fully built out already, see those two CLAUDE.md files for editor-integration
context.

## Verilog IR — a naming gotcha, not a stub

`analyzer/src/main/kotlin/com/uabutler/verilogir/` contains exactly **one file**,
`builder/creator/util/Identifier.kt` — a pure name-mangling utility (`Identifier.wire()`,
`Identifier.module()` for disambiguating generic instantiations), imported directly by
`netlistir/netlist/Module.kt`/`MutableModule.kt` for human-readable error messages and by
`compiler`'s real Verilog generator for consistent naming. **This is not Verilog code generation** —
the actual Verilog AST, builder, and serializer live entirely in `compiler`'s own (much larger,
same-named) `verilogir/` package. Don't go looking for Verilog-generation logic here, and don't assume
this package is a leftover stub to delete.

## Standard library

`util/StandardLibrary.kt`'s `StandardLibraryFunctions` enum lists every stdlib-*defined* helper
(written in GAPL itself — `replicate`, `vector_map`, `vector_zip`, `unpair`, etc., several of them
recursive); its `standardLibary` string is the literal GAPL source text prepended per the trick above.
`util/PredefinedFunctionNames.kt` is the disjoint set of true compiler *primitives* (`register`,
`integer_register`, `add`, `mux`, `literal`, etc. — no GAPL source, `Scope.resolve()` treats these
specially with no declaration site). `netlistir/util/PredefinedFunction.kt` is the netlist-IR-level
realization of each primitive, one sealed subtype per operation, dispatched from a `Module.Invocation`
via `PredefinedFunction.search()`.

## Known gotchas

- **Reference equality, not structural equality, for `Node`/`Wire` and anything keyed by them.**
  `Node`/`Wire` are plain `sealed class`es (no `equals`/`hashCode` override) on purpose — two node
  objects are only equal if they're the same object. `Graph.kt`'s internal adjacency/SCC bookkeeping
  is built on `IdentityHashMap`-backed maps/sets for the same reason: `WeightedGraph.Node`/`Edge`
  wrapper types *are* data classes, so two genuinely distinct graph positions (e.g. the same generic
  function inlined from two different call sites) can coincidentally wrap structurally-equal
  `(weight, value)` pairs — relying on structural equality there previously let unrelated call sites
  silently merge into one shared adjacency entry, corrupting the graph. Keep any new lookup on these
  types identity-based.
- **The stdlib line-offset is computed by counting `\n` in the raw `standardLibary` string**, not
  derived any other way — if `StandardLibrary.kt`'s formatting ever changes (an extra blank line, a
  changed leading blank), `stdLibLineOffset` needs re-verifying by hand.
- **`Logger` defaults to `DEBUG` and writes via bare `println`.** Same "stdout is sacred" hazard as
  the LSP server (`../lsp/src/main/kotlin/com/uabutler/lsp/Main.kt` redirects `System.out` to protect
  against exactly this) — any new entry point embedded in something stdio-protocol-sensitive needs to
  set the log level down or redirect stdout before any analyzer code runs. Tests set
  `Logger.setLevel(Logger.Level.WARN)` in a `@BeforeEach` to quiet this.
- **`ObjectUtils.toStringBuilder`** exists so `Node`/`Wire`/`WireVector`/`WireVectorGroup`/`Module`
  can implement safe `toString()` despite circular back-references (`Node.parentModule`, etc.) —
  renders anything passed as an `identityProps` entry as `ClassName@identityHash` instead of
  recursing. Any new IR type with a parent back-reference should follow this pattern rather than
  relying on default `data class` `toString()`.
- **Compile time on large designs hasn't been profiled.** `ModuleBuilder.kt` has a `TODO` noting the
  whole "Netlist Builder" stage (ordinary per-function building, plus the combinational-loop check)
  took ~72s on `simtest/tests/aes/test.gapl` — not yet known which part is the actual bottleneck. Not
  urgent, but worth knowing before assuming an unrelated change caused a slowdown.

## Testing conventions

`src/test/kotlin/` has `diagnostics/` (`ResolverDiagnosticsTest.kt`, `NetlistBuilderDiagnosticsTest.kt`)
and `semantictokens/` (`SemanticTokensTest.kt`). The shared helper is
`diagnostics/AnalyzerDiagnosticsTestUtil.kt`: `compileExpectingDiagnostics` (calls `Analyzer.analyze`,
resolve-only) and `compileFullExpectingDiagnostics` (calls `Analyzer.analyzeFull`, needed for any
`BuilderDiagnosticKind`) — both `check()` that diagnostics are non-empty and both catch
`DiagnosticsException` (thrown for syntax errors rather than returned). `defaultOptions` sets
`includeStdLib = false` — tests write minimal self-contained GAPL and don't want stdlib line-offset
noise in their span assertions; only opt into `includeStdLib = true` when a test specifically needs a
real stdlib function.

Pattern for a new test: pick the right package, write GAPL as a `""".trimIndent()"` string, call the
matching helper, assert with `assertIs<SpecificDiagnosticKind>(...)` rather than matching message
text (kind types are the stable contract; message wording isn't). `SemanticTokensTest.kt`'s
`positionOf(text, needle, occurrence)` helper is worth reusing for any test that needs a line/column
without hand-counting it in the source string.
