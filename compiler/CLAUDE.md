# CLAUDE.md

This file provides guidance to Claude Code when working in this directory.

## What this is

The actual GAPL compiler: takes `analyzer`'s netlist IR (already parsed, resolved, and diagnostic-clean),
runs a transformer pipeline over it (flattening, simplification, retiming), and serializes the result
to Verilog. Also home to the `gapl` CLI (`GAPL.kt`) and its Debian/RPM packaging. See the root
`../CLAUDE.md` for the overall module graph — `compiler` depends on `analyzer` and nothing depends on
`compiler`.

If you're debugging something that looks like a parsing/resolution/diagnostic bug (wrong error, wrong
go-to-definition, wrong semantic token), that's almost certainly `../analyzer`, not here. This
directory is where a *correctly-built* netlist can still turn into wrong or suboptimal Verilog, or
where retiming/flattening/CLI behavior lives.

## Architecture

### Pipeline entry points

- `GAPL.kt` — CLI entry point, built on [Clikt](https://ajalt.github.io/clikt/) (pinned to 5.0.0, not
  latest — see gotchas). `Gapl` (a `CliktCommand`) declares each flag as a typed, validated property
  and builds `Compiler.Options` directly from them in `run()`; unrecognized flags, missing required
  options, and bad choice values are all rejected by Clikt itself, with `--help` generated from the
  same option declarations rather than hand-maintained separately. `compile()` reads/joins input
  files, calls `Compiler.compile`, and distinguishes three outcomes: `DiagnosticsException` (your GAPL
  is wrong — formatted diagnostics, exit 1), `InvalidCompilerOptionsException` (your flags are wrong —
  e.g. `--flatten`/`--retiming-solver` kind mismatch — clean "Error: ..." message, exit 1), or any
  other `Throwable` (the compiler is wrong — generic "contact a TA" message, stack trace at ERROR log
  level, exit 1).
- `Compiler.kt` — `Compiler.compile(gapl, options)` calls `Analyzer.analyzeFull(gapl, options.analyzerOptions)`,
  throws `DiagnosticsException` if any diagnostics came back, otherwise runs `runNetlistTransformers`
  over `analysis.modules!!` and serializes to Verilog.

### Transformer pipeline

Every transformer implements `netlistir/transformer/Transformer.kt`'s one-method interface
(`transform(original: List<Module>): List<Module>`). `Compiler.runNetlistTransformers` builds a
conditional list and folds over it, **in this order**:

1. **`Flattener`** (only if `--flatten` isn't `none`) — inlines `ModuleInvocationNode`s. `ALL` mode
   fully inlines everything (breadth-first from `InvocationGraph`'s roots down to zero invocations);
   `RECURSIVE` only inlines *self*-recursive calls, leaving other module calls as separate modules.
2. **`StandardLibraryFilter`** (only if stdlib was included) — the stdlib is textually prepended
   source (see `../analyzer/CLAUDE.md`), so its functions show up as ordinary root modules after
   parsing; this drops the redundant standalone stdlib module definitions from the final output
   (their uses are already inlined/synthesized elsewhere).
3. **`ConstantSimplifier`** (only if `--constant-simplification`) — **do not enable this**, see gotchas.
4. **`LiteralSimplifier`** (default on, `--no-literal-simplification` to disable) — dedupes identical
   literal nodes to one canonical node per `(size, value)` signature.
5. **`PassThroughRemover`** — always runs. Cleans up `PassThroughNode`s left behind as inlining shims
   by `Flattener`, rewiring sinks directly to sources.
6. **`Retimer`** (only if `--retime` was passed) — see below.
7. **`Renamer`** — always runs, last. Gives every body node a short synthetic name (`node0`, `node1`,
   ...) per module — post-flattening names get extremely long (concatenated invocation chains), which
   can be a problem for downstream synthesizers.

### Retiming

The most complex part of this module. Leiserson-Saxe retiming: each node gets an integer lag `r(v)`;
a retimed edge's register count is `w(e) + r(sink) - r(source)`. Two modes, chosen by
`Compiler.runNetlistTransformers` based on `--flatten`: `Flattener.Mode.ALL` → `Retimer.Mode.MONOLITH`
(each module retimed independently); `RECURSIVE`/`NONE` → `Retimer.Mode.HIERARCHICAL` (modules retimed
bottom-up, coordinated across the call graph).

- `netlistir/transformer/Retimer.kt` — top-level dispatch between the two modes.
- `netlistir/transformer/util/retiming/Retiming.kt` — the core lag bookkeeping and
  `findMinimumClockPeriod` (binary search over all achievable clock periods using a solver's
  feasibility check as the oracle).
- `netlistir/transformer/util/retiming/HierarchicalRetimer.kt` — builds
  `HierarchicalLeisersonCircuitGraph`s for every module and delegates to
  `HierarchicalMinimalRegisterSolver`, which solves bottom-up (leaf modules first) so a caller's
  retiming can be pinned against its already-solved callees' boundary timing.
- `netlistir/transformer/util/retiming/solver/FastSolver.kt` — greedy/iterative, does **not**
  minimize register count, just finds *a* feasible retiming for a target clock period (bounded
  iteration count). Used as the cheap oracle for `findMinimumClockPeriod`'s binary search.
- `netlistir/transformer/util/retiming/solver/MinimalRegisterSolver.kt` — the true optimal solver, an
  ILP formulated with Google OR-Tools CP-SAT (Leiserson-Saxe's standard formulation: minimize
  `Σ (fanIn(v) - fanOut(v)) * r(v)`, subject to non-negative edge weights, clock-period constraints
  only on paths that actually need them, and an anchor constraint since retiming is only defined up
  to a global constant). Slower than `FastSolver`, used only when a `minimal-register` family solver
  is selected via `--retiming-solver`/`--retiming-min-clock-period-solver`.
- `netlistir/transformer/util/retiming/solver/HierarchicalMinimalRegisterSolver.kt` — orchestrates
  `MinimalRegisterSolver` per-module across a hierarchy: rather than inlining already-solved children
  wholesale, it "expands" each into a small synthetic summary (boundary nodes carrying the child's
  already-computed `TimingProperties`) so each module's own ILP stays small regardless of call-tree
  depth, then pins the child's boundary via equality constraints so its own already-solved timing
  can't be silently changed.

The delay model: `PropagationDelay` (interface, actually defined in
`../analyzer/src/main/kotlin/com/uabutler/util/PropagationDelay.kt` since the graph-conversion code
that consumes it lives there too) is implemented here by `util/YamlDelayModel.kt`, a snakeyaml-based
parser for `--retime`'s YAML file — maps operator names to per-bit-width delay values. Flow:
`--retime FILE` → `GAPL.kt` → `YamlDelayModel` → `Compiler.Options.retime` → `Retimer` →
`NetlistLeisersonCircuitConverter.fromModule` (analyzer-side) assigns per-node combinational-delay
weights when building the graph.

**Note on where the graph classes actually live**: `LeisersonCircuitGraph`/`HierarchicalLeisersonCircuitGraph`
themselves, and the netlist↔graph converters (`NetlistLeisersonCircuitConverter`,
`HierarchicalNetlistLeisersonCircuitConverter`), all live in `../analyzer` now (moved there so an
analyzer-side combinational-loop diagnostic could reuse them without needing OR-Tools/snakeyaml in
`analyzer`). Only the *solving/orchestration* logic above — which needs OR-Tools and has real netlist-type
coupling — stayed here. See `../analyzer/CLAUDE.md` for those.

### Verilog output

`compiler` has its **own**, completely separate Verilog IR and serializer under `verilogir/` — not
reused from `analyzer` (whose own `verilogir/` package is nearly empty, just a shared `Identifier.kt`
naming-scheme helper). Pipeline: `VerilogBuilder.verilogModuleFromGAPLModule(module)` turns a
`netlistir.netlist.Module` into a `verilogir.module.Module` (name, ports from `InputNode`/`OutputNode`,
statements via `StatementBuilder`), then `.verilogSerialize()` turns that into text.
`StatementBuilder` does two passes per node: `createNodes` (declarations/instantiations, one creator
per node kind under `builder/creator/` — `PredefinedFunctionNodeCreator.kt` is the largest file in the
subproject, one branch per primitive operation) and `connectNodes` (wire connections → `Assignment`
statements, with range-coalescing so consecutive same-source-vector bit connections collapse into one
`[hi:lo]` assignment instead of one per bit).

### Packaging

`build.gradle.kts` uses the `application` plugin (`mainClass = "com.uabutler.GAPLKt"`,
`applicationName = "gapl"`) plus `com.netflix.nebula.ospackage` for `buildDeb`/`buildRpm` — both stage
`installDist`'s output at `/usr/lib/gapl`, symlink `/usr/bin/gapl`, and declare a Java 17 runtime
dependency. `jvmToolchain(17)` matches. `./gradlew :compiler:installDist` is the normal way to get a
runnable `gapl` binary at `compiler/build/install/gapl/bin/gapl` without building a package.

## Known gotchas

- **Clikt is pinned to 5.0.0, not the latest 5.1.x.** Clikt >=5.0.2 is compiled against Kotlin 2.1+
  metadata; this repo's Kotlin plugin is pinned to 2.0.21 (see root `../CLAUDE.md` on the Gradle/Kotlin
  version constraints), and a newer Clikt fails at `:compiler:compileKotlin` with "Module was compiled
  with an incompatible version of Kotlin." 5.0.0 is the last release built against Kotlin 2.0.0.
  Bumping Clikt requires bumping the project's Kotlin version first (untested — see the root
  `../CLAUDE.md` warning about Gradle/Kotlin version bumps having broken the build before).
- **`--constant-simplification` always crashes if passed.** It's a `hidden = true` option in
  `Gapl` (parseable, but excluded from `--help`) precisely because `Compiler.runNetlistTransformers`
  calls an unconditional `TODO()` right after adding `ConstantSimplifier` to the pipeline, so this
  flag always throws `NotImplementedError`. The underlying `ConstantSimplifier.kt` logic is actually
  mostly implemented (full bit-array constant folding for binary/unary ops and registers) except
  `MuxFunction`/`DemuxFunction`/`PriorityFunction`, which are separately stubbed `TODO("dont wanna")`
  — so even fixing the outer crash wouldn't make this fully functional yet.
- **Hierarchical retiming has hard cross-flag constraints, driven implicitly by `--flatten`, not
  obviously by anything `--retime`-related.** Any `--flatten` mode other than `all` forces
  `Retimer.Mode.HIERARCHICAL`, which requires `--retiming-solver`/`--retiming-min-clock-period-solver`
  to be a hierarchical solver (currently just `hierarchical-minimal-register`) — an explicit monolithic
  solver choice with a non-`all` flatten mode throws `InvalidCompilerOptionsException` (`Compiler.kt`'s
  `resolveFlattenMode`/`resolveMinClockPeriodSolverId`), reported by `GAPL.kt` as a clean CLI error, not
  an internal-error crash. `--retiming-maintains-timing` is similarly incompatible with hierarchical
  mode (`Retimer.transform`), same exception type.
- **`--log-level`'s default is `error`**, matching what `--help` now shows (Clikt derives the displayed
  default from the option declaration itself, so this can no longer silently drift the way the old
  hand-written help text once did). Pass `--log-level info` (or `debug`) explicitly if you're not
  seeing expected log output.
- **`LeisersonCircuitGraph`'s constructor is itself a cycle check that hard-crashes** (`IllegalArgumentException`)
  if the zero-weight-edge subgraph has a cycle — this is a real, unrecoverable error path used during
  retiming, distinct from `../analyzer`'s own graceful combinational-loop diagnostic. If you need to
  inspect *which* nodes are in a cycle instead of just crashing, use
  `HierarchicalLeisersonCircuitGraph.flattenToWeightedGraph()` instead of `.flatten()` — the escape
  hatch exists specifically because of this.
- **Every generated Verilog module implicitly gets `clock`/`reset`/`enable` ports**, hardcoded in
  `verilogir/module/Module.kt`'s `verilogSerialize()` — not visible anywhere in GAPL source or the
  netlist IR itself. Don't be surprised comparing a generated module's port list against its GAPL
  declaration.
- **Two unrelated `verilogir` packages exist** — `../analyzer`'s is nearly empty (one shared naming
  helper), `compiler`'s is the real, full Verilog AST + serializer. Grepping "verilogir" across the
  repo can be misleading about which one actually renders anything.
- **Compile time on large designs can be substantial and hasn't been profiled.** `LiteralSimplifier`
  alone took ~198s compiling `simtest/tests/aes/test.gapl` (has a `TODO` there noting this, worth
  checking `module.getConnectionsForNodeOutput`/`connect`/`disconnect` for hidden O(n²) behavior
  before assuming it's just "large designs are slow"). Not urgent, but real.

## Testing

`src/test/kotlin/` contains only a `graph/` package (six files: `TestUtil.kt`/`HierarchicalTestUtil.kt`
DSL helpers for building small string-keyed test graphs by edge list, plus tests for
`LeisersonCircuitGraph`, clock-period minimization, register minimization, and the hierarchical
solver). These are pure algorithm tests against synthetic hand-built graphs — no test coverage exists
for `Compiler.kt`/`GAPL.kt`/`Flattener`/`Renamer`/`PassThroughRemover`/the Verilog builder themselves.
Worth knowing if you're touching the pipeline orchestration or Verilog emission and looking for
existing test patterns to extend — there currently aren't any at that level, only at the retiming-math
level.
