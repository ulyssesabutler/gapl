# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

GAPL (Gate Array Programming Language) is a WIP hardware description language and its compiler, which
targets Verilog. The compiler is a Kotlin/JVM multi-project Gradle build. GAPL source (`.gapl` files)
describes circuits as compositions of functions/interfaces with generic (compile-time) parameters;
the compiler builds an internal netlist IR, runs transformation passes over it (flattening, standard
library filtering, retiming, renaming, etc.), then lowers to a Verilog IR and serializes Verilog text.

## Build & test commands

- Build the compiler CLI: `./gradlew :compiler:install` (produces a runnable distribution at
  `compiler/build/install/gapl/bin/gapl`)
- Run compiler unit tests: `./gradlew :compiler:test`
  - Run a single test class: `./gradlew :compiler:test --tests "graph.RegisterMinimizationTest"`
  - Run a single test method: `./gradlew :compiler:test --tests "graph.RegisterMinimizationTest.someTestName"`
  - Unit tests live under `compiler/src/test/kotlin` and currently focus on the retiming/graph
    algorithms (`graph/` package): `LeisersonCircuitGraphTest`, `RegisterMinimizationTest`,
    `ClockPeriodMinimizationTest`, `NewHierarchicalMinimalRegisterSolverTest`. There is no test
    coverage yet for the hierarchical retiming end-to-end behavior (see "Current work" below).
- End-to-end Verilog simulation tests (compiles `.gapl` → Verilog → lints/runs with Verilator):
  `./gradlew :simtest:build` (or the sub-tasks `:simtest:simtestGenerateVerilog`,
  `:simtest:simtestCompile`, `:simtest:simtestRun`). Requires `verilator` on `PATH`. This is what CI
  runs (`.github/workflows/main.yml` invokes `./gradlew :simtest:runSimulation`).
  - There is no per-test filter; every directory under `simtest/tests/` runs each time.
  - To add a test: create `simtest/tests/<name>/` containing one or more `.gapl` files and a C++
    Verilator harness (`*.cpp`); optionally a `top.txt` naming the top module and a
    `test.properties` file controlling compiler flags (flatten mode, retiming, waveform dump, etc. —
    see `simtest/build.gradle.kts` for the full property list). See `simtest/README.md` for details.
- Compile a `.gapl` file manually with the CLI: `gapl -i INPUT.gapl [...] -o OUTPUT.v` (run
  `gapl -h` for all flags: `-flatten none|all|recursive`, `-retime DELAY_MODEL.yaml`,
  `-retiming-clock-period min|N`, `-retiming-minimize-register-count`,
  `-retiming-maintains-timing`, `-no-std-lib`, `-log-level`).
- The `antlr` subproject holds the grammars (`antlr/src/main/antlr/GAPL.g4`, `CST.g4`) and generates
  the Kotlin lexer/parser consumed by `compiler`.
- `latency-finder`, `basys`, and `netfpga` are downstream/hardware-target projects (FPGA synthesis,
  timing analysis) built on top of the compiler output; they require Vivado/hardware tooling not
  present in most dev environments — see `netfpga/README.md` and `simtest/README.md` before touching
  those.

## Architecture: compilation pipeline

The pipeline (see `settings.gradle.kts` header comment and `compiler/src/main/kotlin/com/uabutler/Compiler.kt`):

```
GAPL source (.gapl)
  → ANTLR grammar (antlr/) → generated Lexer/Parser
  → CST (com.uabutler.cst)          — concrete syntax tree, close to grammar shape
  → Resolver (com.uabutler.resolver) → AST (com.uabutler.ast) — scope resolution, name binding
  → ModuleBuilder (com.uabutler.netlistir.builder) → Netlist IR (com.uabutler.netlistir.netlist)
  → Transformer passes (com.uabutler.netlistir.transformer), run in order:
      Flattener → StandardLibraryFilter → ConstantSimplifier (WIP/broken) → LiteralSimplifier
      → PassThroughRemover → Retimer (optional) → Renamer
  → VerilogBuilder (com.uabutler.verilogir.builder) → Verilog IR (com.uabutler.verilogir.module)
  → VerilogSerialize → Verilog text (.v)
```

`compiler/src/main/kotlin/com/uabutler/Compiler.kt` (`Compiler.compile`) is the canonical entry point
that wires all these stages together; start there to trace how a change to one stage affects the
whole pipeline. `Compiler.Options` documents every compiler flag.

### Netlist IR: `Module` vs `MutableModule`

`com.uabutler.netlistir.netlist.Module` is the immutable form produced by `ModuleBuilder` and passed
between transformer stages as the on-the-wire type. `MutableModule` (subclass) is used internally by
transformers that need to build/rewrite a netlist (e.g. `Retimer`, `Flattener`) — call
`Module.toMutableModule()` to get a mutable copy, mutate it via `connect`, node creation, etc., then
treat the result as a `Module` again. `NodeCopier` handles the wire-for-wire copying used both by
`toMutableModule()` and by flattening/inlining.

A `Module` is a graph of `InputNode`/`OutputNode`/`BodyNode` connected by `Connection`s between
`OutputWire`s and `InputWire`s (grouped into `WireVector`/`WireVectorGroup`). `Module.Invocation`
identifies a module by GAPL function name + resolved generic interfaces/parameters — i.e. each
distinct instantiation of a generic function becomes its own `Module`.

### Retiming (active area of development)

Retiming moves registers through a circuit to optimize clock period / register count while
preserving behavior, based on Leiserson-Saxe retiming theory. Key pieces:

- `util.graph.LeisersonCircuitGraph` / `WeightedGraph` — generic weighted-graph representation used
  for retiming, with clock-period and register-count analysis.
- `util.graph.util.Retiming`, `FastSolver`, `MinimalRegisterSolver` — the core retiming algorithms
  (minimum clock period search, feasible retiming for a target clock period, minimal register count
  under a clock period constraint).
- `netlistir.transformer.util.NetlistLeisersonCircuitConverter` — converts a `MutableModule` netlist
  to/from a `LeisersonCircuitGraph` so the generic graph algorithms can operate on circuit IR.
- `netlistir.transformer.Retimer` — the `Transformer` pass. Has two modes:
  - `MONOLITH`: flattens first, retimes the whole circuit as one graph (`transformPiecewise`).
  - `HIERARCHICAL`: retimes each module in the hierarchy independently and composes results
    (`transformAll`, delegates to `HierarchicalRetimer`) — this is the newer "hierarchical retiming
    abstraction" and does not yet support `maintainTiming`.
- `util.graph.HierarchicalLeisersonCircuitGraph` / `NewHierarchicalLeisersonCircuitGraph` and
  `util.graph.util.HierarchicalMinimalRegisterSolver` / `NewHierarchicalMinimalRegisterSolver` —
  hierarchical variants of the above, where a module's "contracted" timing behavior (as seen from
  outside) must match the theory described in `brainstorming/` before/after retiming. Note the
  `New*`-prefixed classes are a newer generation superseding the non-prefixed ones during active
  rework — check which one is actually wired into `Retimer`/`HierarchicalRetimer` before assuming
  either is dead code.
- `netlistir.transformer.util.InvocationGraph` tracks the call/instantiation hierarchy between
  modules, used to retime bottom-up.

Per the recent commit history, the hierarchical retiming path is newly introduced and existing tests
don't yet exercise it — if working in this area, check whether test coverage needs to be added
alongside behavioral changes rather than assuming existing green tests mean hierarchical retiming is
correct.

### Standard library & predefined functions

`util.StandardLibrary` embeds a GAPL-source standard library (interfaces like `boolean`, `pair`,
`valid`; functions like `vector_map`, `mux`, arithmetic/bitwise ops — enumerated in the top-level
`README.md`) that is prepended to user source unless `-no-std-lib` is passed.
`netlistir.transformer.StandardLibraryFilter` strips/rewrites stdlib-originated modules post-build so
they work with non-flattened (hierarchical) designs. Primitive operations that don't have a GAPL-level
definition (registers, muxes, literals, arithmetic) are backed by `PredefinedFunction` implementations
(`netlistir/util/PredefinedFunction.kt`, named in `util/PredefinedFunctionNames.kt`) and represented in
the netlist as `PredefinedFunctionNode`.

### Propagation delay / timing model

`util.PropagationDelay` is the interface for per-operation timing used by retiming;
`util.YamlDelayModel` loads a concrete delay model from a YAML file (see `-retime` flag,
`simtest/tests/*/delay.yaml`, `latency-finder/delay.yaml`).

## Other subprojects

- `latency-finder`: standalone Kotlin tool for extracting timing/slack info from Vivado logs and
  driving NetFPGA builds to find achievable clock periods (`SlackFinder`, `ClockPeriodFinder`,
  `NetFPGABuilder`, `LogHandler`). Logs of past runs are checked in under `latency-finder-logs/`.
- `basys` / `netfpga`: hardware targets that combine compiler-generated Verilog with hand-written
  RTL/testbenches and Vivado build scripts (`scripts/*.tcl`) to synthesize/simulate on real FPGA
  boards. These depend on Xilinx Vivado being installed and licensed (path configured via
  `vivadoSettings` in `gradle.properties`) and are not part of the normal compiler dev loop.
- `brainstorming/`: design notes and theory writeups (not code) — useful background reading for the
  retiming/interface/parser design rationale referenced above, but not authoritative on current
  behavior; verify against code.
- `python-tests/`: ad hoc Python scripts, unrelated to the Gradle build/test graph.
