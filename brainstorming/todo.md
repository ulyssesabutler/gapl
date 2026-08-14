# TODOs

## Validation
There are a few different validations we need to do, but currently don't.

### Type checking
- We need to validate that every connection connects two compatible types
  - E.g., only connect two wire vectors if they're the same length
- We need to validate parameters
  - There is a bit of complication here, specifically, with function parameters.
    - These might depend on other parameters to evaluate

## Retiming

> The plan for fixing the first two entries below lives in
> `brainstorming/per-port-hierarchical-retiming.md` — that doc is the design, this section is the
> backing detail (measurements, reproductions, failure modes).

- **A parent-side feedback loop through a submodule is why CMS retimes monolithically but not
  hierarchically.** Root cause is that the boundary summary is per-*module*, not per-*port*: a module
  hands its parent one `delta`, one `inputDelay` (max over every input port) and one `outputDelay`
  (max over every output port). When a parent has a feedback loop through the child, the loop's
  register count is invariant under retiming, and both of those merged summaries charge the loop for
  paths that need not be on it. It fails in two distinct ways, with `loopRegisters` counted in the
  parent and `delta` from the child:

  - **Register count.** `delta > loopRegisters` means the child's own retiming wants more registers
    on the loop than the loop has, so the loop's external edges would have to go negative. Infeasible
    outright, and no clock period fixes it.
  - **Delay concatenation.** `delta == loopRegisters` is satisfiable, but it consumes every register,
    pinning the external return edge to zero - so the parent then has to fit
    `outputDelay + inputDelay` into a single clock period. With `loopRegisters - delta >= 1` the
    parent only has to fit `max(inputDelay, outputDelay)`.

  15-line repro of the *second* mode (`--flatten none` vs `--flatten all`, `default: 1` delay model).
  `updater` reports `delta=1 inputDelay=2 outputDelay=2` against a one-register loop:
  ```
  function updater() data: wire[32], state: wire[32] => o: wire[32] {
      data, data => add(32) => declare h1: wire[32];
      h1, h1 => add(32) => declare h2: wire[32];
      h2, h2 => add(32) => declare h3: wire[32];
      h3, state => bitwise_xor(32) => o;   // `state` is the port the parent's loop runs through
  }
  function test() i: wire[32] => o: wire[32] {
      declare state: wire[32];
      i, state => declare u: updater() => declare next_state: wire[32];
      next_state => register(wire[32]) => state;
      state => o;
  }
  ```
  Monolithic succeeds at every period. Hierarchical is infeasible at periods 1-3 and succeeds from 4
  = `inputDelay + outputDelay`; adding a second register to the loop moves the threshold to 2 =
  `max(inputDelay, outputDelay)`. In the real circuit the loop's combinational depth is just the xor.

  **CMS fails by the first mode, not the second.** `packet_body_processor` is the only module in the
  whole design with a child instance on a feedback loop, and the only infeasible one. The child is
  `count_min_update_sketch4` (ports `value` = the long hash path, not on the loop; `original` = the
  sketch, on the loop; output `updated`), sitting on a two-node loop that holds the single
  `current_sketch` register. Its boundary summary:

  | period | delta | inputDelay | outputDelay | binding |
  |--------|-------|-----------|------------|---------|
  | 20     | 21    | 3         | 11         | register count (21 > 1); delays would have fit |
  | 100    | 3     | 100       | 83         | both (3 > 1, and 183 > 100) |

  As the period loosens `delta` shrinks but the delays grow, and there is no period where both modes
  pass - which is why it is infeasible at 20, 30, 40, 60 and 100 alike. It also explains why adding
  one extra register to the loop does nothing (tried): at period 20 you would need 21. Confirmed
  causally by feeding `updater` from the already-present, commented-out constant `initial_sketch`
  instead of `current_sketch`, taking the child off the loop and changing nothing else - CMS then
  compiles at period 20 with 858 registers.

  **The fix needs per-port lags; per-port delay summaries alone are not enough.** Letting each port
  have its own lag lets the child pipeline the `value` path (delta 21) while leaving the `original`
  path at delta 0, so the loop gains nothing - this addresses the register-count mode, which is the
  one CMS actually hits. Per-port delays only address the delay-concatenation mode, and at period 20
  CMS's delays already fit, so they would do nothing there on their own. Both are worth having: the
  toy needs the delays, CMS needs the lags, and contracting to one node per port instead of one
  `c_i`/`c_o` pair also removes the false combinational loops in the next entry.

  Per-port lags generalise the interior/exterior contract without disturbing its structure: instead
  of one scalar `delta` plus `r(i_j) = r(s_i)`, the child hands up a vector of relative port offsets
  and the parent is constrained to reproduce them, `r_e(c_i_j) - r_e(c_i_1) = r_i(i_j) - r_i(i_1)`,
  with one free global `t` per instance exactly as now. The composition `r(v) = r_i(v) + t` and the
  four-case non-negativity argument carry over unchanged. The visible consequence is that a module's
  ports end up at different pipeline depths - but monolithic retiming already produces exactly that
  skew internally, it just has no module boundary at which to name it.

  Under per-port summaries `combinationalDelay` becomes a per-(input port, output port) matrix,
  populated only for pairs that are still combinationally connected after retiming - it is that
  sparsity that removes the false loops below, since today's single `d_c` asserts every input is
  combinationally coupled to every output.

- **Module-boundary contraction turns legal designs into false combinational loops.** Both the
  super-source/super-sink construction (every module's input ports collapse to one node, every
  output port to another) and the per-child contracted graph (`c_i`/`c_o` joined by a combinational
  `d_c` path) throw away which *port* depends on which. Any external combinational path from an
  output port of an instance back to an input port of that same instance therefore becomes a
  zero-weight cycle, even when the real circuit is acyclic. Minimal repro - a pure DAG that compiles
  fine unretimed and under monolithic retiming, and dies under hierarchical retiming with
  `IllegalArgumentException: Graph cannot contain zero-weight cycles`:
  ```
  function inner() a: wire[32], b: wire[32] => x: wire[32], y: wire[32] {
      a => register(wire[32]) => x;   // registered:    a -> x
      b, b => add(32) => y;           // combinational: b -> y
  }
  function test() i: wire[32] => o: wire[32] {
      declare fb: wire[32]; declare xo: wire[32]; declare yo: wire[32];
      i, fb => declare u: inner() => xo, yo;
      xo, xo => add(32) => fb;        // x -> ext -> b, never a cycle in the real circuit
      yo => o;
  }
  ```
  It fires in two independent places: `HierarchicalRetimingProblem.computePossibleClockPeriods()`
  via `flatten()` (super-node merge), and `HierarchicalMinimalRegisterSolver`'s own flat graph
  (contraction). The analyzer's `CombinationalLoopDetector` correctly clears this design, since it
  tracks port-to-port reachability - so only the retimer disagrees. Note the retiming is *sound*
  here rather than wrong (a re-entrant combinational path is exactly what the contraction cannot
  represent, so it gets rejected rather than under-constrained), but this is a completeness loss
  the paper doesn't mention, and a "contact a TA" crash rather than a diagnostic. A real fix means
  keeping per-port-group reachability in the summary instead of one `c_i`/`c_o` pair.
- The retimed contracted graph's input-delay path always carries exactly one register
  (`w_r(d_i, d_o) = 1`), even when the child really needs `k > 1` between its ports. That
  understates `W(u, v)` for parent paths crossing the child, which only *strengthens* the
  clock-period constraint `r(v) - r(u) >= 1 - W`, so it is sound - but it over-pipelines the parent
  around a deeply-pipelined child. Setting the weight to `-delta + max(1, W_retimed)` would be both
  sound and more accurate; deliberately not done as part of the boundary-tracking fix, since it
  changes generated Verilog for reasons unrelated to that bug.
- `HierarchicalNetlistLeisersonCircuitConverter.fromModule` hangs the super-source off *every*
  source-less node, not just `InputNode`s - so `MinimalRegisterSolver`'s zero-register boundary
  constraint pins literals/constants (and zero-argument child instances) to `r(s_i)` too. That is a
  deliberate fix for a bug found in a real application, but it is a mismatch with the paper (whose
  constraint is over input ports only) and it costs real freedom: a constant feeding a long
  combinational chain cannot be given its own lag. Untangling it needs the boundary to stop being
  inferred from "has no incoming edges" at the same time.
- `MinimalRegisterSolver` treats any CP-SAT status other than `OPTIMAL` as failure, so a `FEASIBLE`
  result (solver hit a limit before proving optimality) is discarded even though it is a perfectly
  valid retiming, just possibly not a minimal one.
- `HierarchicalMinimalRegisterSolver.solveOrNull` re-solves the *entire* hierarchy from scratch on
  every `findMinimumClockPeriod` probe, and `HierarchicalRetimingProblem.computePossibleClockPeriods`
  fully flattens every module - re-flattening a shared child once per call site, the same pattern
  that cost `CombinationalLoopDetector` 696s on netfpga's aes processor before it was rewritten to
  bottom-up port summaries. This is why `--retiming-clock-period min` is impractically slow on
  anything the size of md5 under hierarchical retiming.
- Naively calling `HierarchicalLeisersonCircuitGraph.flatten()` on an already-*retimed* graph is
  unsafe, not just "architecturally awkward" - it was originally tried for both
  `HierarchicalRetimingProblem`-generic clock-period lookups and `HierarchicalRetimer`'s stats
  logging, and both crashed in practice (`IllegalArgumentException: Graph cannot contain
  zero-weight cycles`) on a real hierarchical `-retiming-clock-period min` run. The retimed
  boundary edge weights `HierarchicalMinimalRegisterSolver` computes are calibrated against its
  synthetic per-level "expansion" topology (phantom input/output/combinational-delay nodes encoding
  the child's already-computed retiming difference), not the child's real internal structure, so
  splicing the real structure back in during a naive flatten can produce something that looks
  like an illegal zero-register cycle even though the retiming itself is correct. Fixed by (a)
  `findMinimumClockPeriod` no longer calling `result.computeClockPeriod()` on a solved result -
  it caches from the tried period instead, which is always correct by monotonicity, just a
  slightly more conservative cache-fill than exploiting an over-achieving solver; and (b)
  `HierarchicalMinimalRegisterSolver` now exposes `timingPropertiesFromLastSolve(root)`, sourcing
  `HierarchicalRetimer`'s stats from the properties actually computed during the solve rather than
  re-deriving them externally. `HierarchicalRetimingProblem.computeClockPeriod()`/
  `computePossibleClockPeriods()` (which still do naively flatten) are only ever safe to call on
  the pristine, never-retimed problem now - worth a stronger type-level guard than a comment if
  this bites again.
- `SccSolver`/`DagSolver` do not enforce the zero-register `VirtualIONode` boundary invariant that
  `MinimalRegisterSolver` enforces (classical host-vertex retiming) - the vendored `retimeByScc`/
  `dagRetime` have no equality-constraint mechanism. Acceptable for now since both are
  feasibility-only (like `FastSolver`), but a real gap if either is ever used as the
  final/register-shaping solver against real module I/O boundaries.
- `DagSolver` (wraps `retiming.dagRetime`) is narrower than every other monolithic solver here: it
  requires the *entire* circuit to be acyclic, not just well-formed - a cycle with a register
  protecting it (the normal shape of any real stateful/feedback circuit) is out of its domain
  entirely, not just a harder case. `DagSolver.solveOrNull` throws (not returns null) if the graph
  isn't a true DAG, since that's a solver/graph-shape mismatch, distinct from a target period being
  genuinely infeasible. Only useful for pure combinational-pipeline circuits with no loops at all.
- `HierarchicalRetimer` hardcodes `HierarchicalMinimalRegisterSolver` internally (for both the
  min-clock-period search and the final solve) instead of taking a resolved `HierarchicalSolver`
  - fine while it's the only implementation, but should take an injected/resolved solver once a
  second `HierarchicalSolver` exists.
- The vendored reference algorithms (source for `SccSolver`/`DagSolver`) now live at
  `compiler/src/main/kotlin/.../retiming/solver/anirudhsk/`, committed directly (no more nested
  git repo/submodule). The files still declare `package retiming` rather than a package matching
  their directory - harmless (Kotlin doesn't require the two to match), but worth cleaning up
  if these are ever touched again.

## NetFPGA build (Gradle)
- `netfpga/build.gradle.kts`'s per-core IP packaging tasks (`packageCore*`, registered via
  `registerNetfpgaCoreBuildTask`) declare `component.xml`/`xgui/` as Gradle `outputs`, so that
  `makeInit`/`makeIPs` skip re-invoking Vivado when nothing changed. This assumes Vivado's
  `ipx::save_core` produces deterministic output content for the same input RTL/tcl - if it
  actually embeds something nondeterministic (a timestamp is the common culprit for Vivado IP
  packaging), Gradle would see the output as "changed since last recorded" on every run and
  re-execute every one of these ~23 tasks unconditionally, defeating the incremental-build point
  of the whole exercise (though not causing incorrect behavior - re-running Vivado is still safe).
  Unverified: this repo's sandbox has no Vivado install to check against. Confirm empirically by
  running `:netfpga:makeInit` twice in a row with nothing changed and checking whether the
  `packageCore*` tasks report `UP-TO-DATE` the second time; if not, likely fix is normalizing/
  stripping the nondeterministic field(s) from `component.xml` before Gradle fingerprints it
  (e.g. via a `normalizeLineEndings`-style content filter, or excluding the offending field with
  a custom `FileNormalizer`), rather than accepting an always-reruns task.
  UPDATE: turns out this machine (a later session) does have Vivado, and real `:netfpga:build`
  runs have repeatedly shown the bulk of `packageCore*` tasks reporting `UP-TO-DATE` on repeat
  invocations (e.g. "43 actionable tasks: 2 executed, 41 up-to-date"), so this concern appears
  unfounded in practice - not rigorously isolated/re-verified in a clean two-runs-in-a-row test,
  but no longer looks like a real problem.

## NetFPGA partial synthesis (see the "partial synthesis" plan discussed with the user)
- `:netfpga:build` now produces a real bitstream end-to-end (verified: 36m42s,
  `reference_switch.bit`), with the GAPL kernel packaged as its own checkpointed IP
  (`lib/hw/contrib/cores/gapl_kernel_v1_0_0/`) so its synthesis is cached across builds where
  `GAPLprocessor.v` doesn't change. Real timing data shows this only saves ~2 minutes though -
  the NetFPGA `control_sub` block design (PCIe/MicroBlaze/crossbars/DMA, 30+ IP sub-synthesis
  runs) dominates the ~30-40 minute build, not GAPL.

- **`:netfpga:build` does not currently no-op even when absolutely nothing changed** (same
  application, `hw/project` left intact) - investigated directly with real Vivado, findings so
  far (each confirmed empirically, not guessed):
  - Removing `reset_run synth_1` (`run_synth.tcl`) was necessary but not sufficient - even with it
    gone, all 30+ `control_sub_*_synth_1` sub-runs and the top-level `synth_1` relaunch on every
    build, verified via two consecutive full builds of the same unchanged application.
  - First hypothesis - my own `gapl_kernel_ip` refresh logic (`upgrade_ip`/`generate_target`,
    added to fix a real separate bug, see below) was itself touching the IP's output products on
    every run and cascading staleness - **ruled out**: made the refresh conditional (only run when
    the packaged core's `component.xml` is newer than the project's `.xci`, compared via `file
    mtime`), confirmed via a cheap standalone Tcl script (`open_project` + property queries, no
    `launch_runs`, seconds not minutes) that the condition correctly evaluates to "skip", then
    reran the full build - still fully relaunched everything.
  - Queried Vivado's own run properties directly (`get_property NEEDS_REFRESH [get_runs ...]`):
    `synth_1` shows `NEEDS_REFRESH=1`, while every individual sub-run (`control_sub_*_synth_1`,
    `gapl_kernel_ip_synth_1`) shows `NEEDS_REFRESH=0`. So `launch_runs synth_1` appears to cascade
    a full relaunch of every prerequisite whenever the *top-level* run needs refresh, regardless of
    each sub-run's own individual freshness - this is likely the actual mechanism, not per-run
    staleness checking the way GUI/docs describe it.
  - Second hypothesis - `update_ip_catalog` (called unconditionally at the top of `run_synth.tcl`)
    causes `synth_1`'s `NEEDS_REFRESH` to flip - **ruled out**: queried `NEEDS_REFRESH` immediately
    after `open_project`, before calling `update_ip_catalog` at all - already `1`. Whatever marks
    `synth_1` as needing refresh happens by the time the *previous* run finished (or on
    `open_project` itself in a fresh batch session), not from anything in this session's own logic.
  - **Not yet resolved**: why `synth_1` is marked `NEEDS_REFRESH=1` persistently, even right after
    its own prior run completed successfully with nothing changed. Possibly `NEEDS_REFRESH` isn't
    actually the property gating `launch_runs`'s rerun decision at all (could be a GUI/report-
    refresh flag rather than a resynthesis-needed flag) and the real mechanism is an internal
    dependency-hash comparison not exposed as a simple property - or genuinely does depend on
    long-lived GUI/Tcl session state that a fresh `-mode batch` process each time never has,
     making true no-op behavior across separate Vivado invocations simply not achievable this way.
    Stopped here per the plan's stop condition (2 real attempts at the same category of fix without
    resolution) rather than continuing to spend ~36-minute Vivado cycles guessing further.
  - **Separately, and already fixed** (`run_synth.tcl`, committed): `create_project` is
    skip-if-exists (`hw/Makefile`), so `create_ip` for `gapl_kernel_ip` only runs once - without an
    explicit refresh, switching applications on an already-created project would silently keep
    building the OLD application. This is a genuine correctness bug independent of the no-op
    question above, now fixed via the conditional refresh described above.

- Given the no-op mystery is unresolved, the original Step 3.0 experiment ("does control_sub get
  skipped when only GAPL changes") is currently moot - if literally *nothing* changing doesn't
  produce a no-op, an app switch won't either. Whoever picks this up next should either (a) find
  the real mechanism gating `launch_runs`'s rerun decision (possibly requires Xilinx support
  docs/forums, not just empirical poking), or (b) accept that Vivado's Project Mode may not
  support build-to-build incrementality this way at all when driven from fresh batch invocations,
  and reconsider whether the "checkpoint everything except GAPL, blackbox + read_checkpoint
  reinject" architecture (discussed with the user, not yet built) is worth pursuing anyway - it may
  face the identical `NEEDS_REFRESH`-cascade problem for the same unexplained reason, so validate
  that concern *before* investing in it, e.g. by testing whether a manually-invoked `read_checkpoint`
  based flow shows the same top-level-run cascade behavior on a small throwaway example first.

- **Update - a real, clean partial win landed despite the Vivado-internal mystery staying
  unresolved**: gave `makeBuild` (`netfpga/build.gradle.kts`) real `inputs`/`outputs` (RTL under
  `hw/hdl/**`, constraints, the gapl_kernel core's `component.xml`), so Gradle itself skips
  invoking `make`/Vivado at all when nothing tracked changed - independent of whatever Vivado's
  own internals do. Verified with real Vivado: a repeat build with nothing changed went from ~40
  minutes to **11 seconds** (`43 actionable tasks: 43 up-to-date`). This doesn't fix the "Vivado
  always relaunches everything internally" mystery, but it does mean the common "I built this
  exact thing already, nothing changed, build again" case is now genuinely fast - a real
  usability win even though the "switch applications quickly" goal (which requires Vivado itself
  to be selective) remains unmet.

- **Confirmed Step 3.0's answer directly**: switching applications (regex -> md5) does *not* skip
  `control_sub`'s sub-runs - same full relaunch as the "nothing changed" case (28+ `control_sub_*`
  matches in the log, `gapl_kernel_ip_synth_1` also relaunched). Consistent with the `NEEDS_REFRESH`
  finding above: since even *zero* changes triggers full relaunch, this was the expected outcome,
  not new information - but it's now been directly verified for the actual app-switch scenario
  rather than inferred. Build still succeeded and produced a correct, differently-sized bitstream
  (regex: 9,367,088 bytes vs md5: 9,721,052 bytes - real evidence of different kernel content, not
  a stale reuse), so correctness held even though performance for this case did not improve.

- **Found a latent bug while confirming the above, currently harmless only by accident**: the
  conditional `.xci` refresh check added to `run_synth.tcl` (compares `component.xml`'s mtime
  against the project's `gapl_kernel_ip.xci` mtime, only calls `upgrade_ip`/`generate_target` when
  the former is newer) incorrectly decided "already up to date, skip" on the md5 switch, even
  though the packaged core's content had just genuinely changed. Observed cause: the `.xci`'s
  mtime was *itself* ~59 seconds newer than the freshly-regenerated `component.xml` - something
  (possibly `open_project` itself touching IP metadata on load) bumps the `.xci`'s mtime
  independent of actual content changes, defeating a simple mtime comparison. This is harmless
  *today* only because Vivado's own blanket "relaunch everything" behavior means
  `gapl_kernel_ip_synth_1` gets resynthesized from the (always-fresh, since
  `packageCoreGaplKernel`'s `doFirst` unconditionally copies current content) source files
  regardless of whether the `.xci` itself got refreshed. If the `NEEDS_REFRESH`-cascade mystery
  above ever gets fixed and Vivado starts being genuinely selective, this check would need to be
  content-hash-based (or otherwise more reliable than mtime) rather than timestamp-based, or it
  could silently reintroduce the "switching apps builds the stale kernel" correctness bug it was
  written to prevent.

- **Root cause of the full-relaunch mystery found (partially) and tested to a decisive negative
  result** - asked to keep investigating after the write-up above, found the actual mechanism
  rather than a property to interpret:
  - `identifier_ip` (a `blk_mem_gen` IP in `create_project.tcl`, `generate_synth_checkpoint false`
    like most cores here) has `CONFIG.Coe_File` pointing at `create_ip/id_rom16x32.coe`, which
    `hw/Makefile`'s `identifier:` target regenerates on *every* `make` invocation (a bare
    prerequisite of `project:`, not conditionally skipped) using `tools/scripts/epoch.sh` -
    literally `date +%s`, a fresh Unix timestamp baked in every single build by design. Confirmed
    empirically: three consecutive `make identifier` runs produced three different file hashes.
  - Confirmed this is *the* mechanism (not a coincidence) via direct, non-full-build testing: with
    `identifier_ip` not yet checkpointed, `launch_runs synth_1` in a fresh session throws
    `ERROR: Run 'synth_1' needs to be reset before launching`, immediately after
    `Generating 'Synthesis' target for IP 'identifier_ip'`. This is almost certainly why the
    original vendored script had unconditional `reset_run` - it was compensating for exactly this.
  - **Fix attempted**: gave `identifier_ip` a checkpoint too (removed its
    `generate_synth_checkpoint false`, same technique as `gapl_kernel_ip`), isolating its
    unavoidable per-build churn into its own tiny sub-run (`identifier_ip_synth_1`) instead of
    feeding directly into the flat top-level elaboration. Ran a real build to establish it (59m8s,
    succeeded, `identifier_ip_synth_1` confirmed as its own run).
  - **Result: did not fix the cascade.** Ran the actual `make`/Vivado flow again directly (bypassing
    Gradle, which otherwise correctly no-ops since `id_rom16x32.coe` isn't a Gradle-tracked input)
    with `identifier_ip` now isolated - all 30+ `control_sub_*_synth_1` sub-runs still relaunched
    identically to before. So identifier_ip's churn was real, confirmed, and worth fixing on its
    own merits (smaller, more correct blast radius for an unavoidable per-build change), but it was
    **not** the (sole) explanation for the full-project cascade.
  - **Updated conclusion**: this now looks more like candidate 2 from the original assessment - the
    top-level `synth_1` run cascades a full relaunch of everything underneath it whenever anything
    changes (even one small, now-isolated IP), regardless of individual sub-run `NEEDS_REFRESH`
    status, as an architectural property of how `launch_runs` treats this flat-top-level-plus-many-
    IPs project structure - not a property-tuning or timestamp-avoidance bug. The one remaining
    untested candidate (candidate 3: does a fresh `-mode batch` process per build fail to trust
    persisted staleness state that a long-lived session would correctly use?) is not cheaply
    testable - it requires a full real synthesis to complete once before a same-session second
    `launch_runs` call means anything, i.e. the same ~40-60 minute cost as everything else here, not
    the near-free cost of the tests that ruled out the other two candidates.
  - The `identifier_ip` checkpoint fix is kept (real, if small, improvement; commit
    `7f015cf`/message references this investigation) even though it didn't solve the bigger
    problem - no reason to revert a correct, defensible change.

- **Update - tested candidate 3 (same-session persistence) directly, decisive negative, but with an
  important, more valuable discovery alongside it**:
  - Ran `launch_runs synth_1` to completion once (from `reset_run`, a clean baseline), then called
    `launch_runs synth_1` *again* immediately - same Vivado process, no `open_project`, nothing
    touched in between. It still threw `ERROR: [Common 17-69] Command failed: Run 'synth_1' needs
    to be reset before launching.` So this rules out "fresh session vs. persistent session" as the
    explanation entirely - it's not about how Vivado is invoked.
  - This also clarifies the actual mechanism: `launch_runs`, at least for this top-level flat run,
    appears to have **no built-in "skip if nothing changed" logic at all** - it isn't that Vivado
    checks staleness and gets it wrong, it's that calling `launch_runs` on an already-`Complete!`
    run unconditionally either (a) errors demanding `reset_run` (same session) or (b) silently
    redoes the work in full (fresh session, no error) - never "recognizes nothing changed and
    skips." The GUI's well-known "click Generate Bitstream twice, second time is instant" behavior
    is most likely implemented by the GUI itself deciding *not to call* `launch_runs` at all when
    it judges nothing changed, not by `launch_runs` doing that judgment internally. This matches
    everything observed all session: Gradle's own tracking (which does exactly this - decides not
    to invoke `make`/Vivado at all - and works, 11s confirmed) is structurally the right kind of
    fix; Vivado's own command surface doesn't do this for you.
  - **Important reframing of the actual cost, found while checking Phase 1's per-run timing**:
    individual sub-runs *did* take their genuine full ~2-3 minutes each in this test's "full redo"
    (confirmed via each one's own `synth_design: Time` log line matching their original synthesis
    time - not a fast skip), but because `launch_runs -jobs 8` runs them in parallel, the *wall-clock*
    cost of a full synthesis redo is only ~12 minutes, not 40+. The ~44-59 minute full-build times
    measured earlier include both synthesis (~12 min) *and* implementation (place & route +
    bitstream) - and this investigation has so far only tested synthesis in isolation.
    Implementation is almost certainly the larger, still-unexamined cost, and it's a *different*
    problem from the synthesis-cascade one investigated above: `impl_1` depends on `synth_1`'s
    output netlist, and since `synth_1` has no skip-if-unchanged behavior either, `impl_1` would
    plausibly always see a "new" netlist and always redo full place & route - **unless** Vivado's
    incremental compile feature (`incremental_checkpoint` on `impl_1`, reusing prior *placement*
    even against a nominally-new-but-logically-similar netlist) helps here, which is exactly the
    scenario it's designed for. This is the "cheaper, lower-effort alternative" mentioned back when
    this whole investigation started and was never actually tried - worth trying now, on the
    implementation side specifically, before considering the bigger checkpoint/blackbox rewrite.

- **Update - implemented and tested incremental compile for `impl_1` (commit `e72d30b`), decisive
  negative result**. `run_impl.tcl` now preserves the routed `.dcp` outside the run directory after
  each successful implementation and sets `incremental_checkpoint` from it on the next run, when
  available. Ran two real builds back to back (same app, nothing else changed) to isolate the
  effect:
  - Baseline (no prior checkpoint, full P&R): `place_design` 4m49s, `route_design` 4m9s, total
    `make` wall-clock 54m35s.
  - With the incremental checkpoint available and confirmed used (`GAPL: found previous routed
    checkpoint, using as incremental_checkpoint`): `place_design` 4m34s, `route_design` 4m5s, total
    `make` wall-clock ~51-52 minutes.
  - The difference (~15s on placement, ~4s on routing, out of ~9 minutes combined) is noise, not a
    real effect. Incremental compile as configured here provides **no meaningful speedup**. Most
    likely explanation: since `synth_1` has no skip-if-unchanged behavior of its own (established
    above), the netlist it produces each time - even for logically identical RTL - is regenerated
    from scratch and likely differs enough at the cell/instance-name level that Vivado's
    incremental placement algorithm can't recognize much as truly matching the previous checkpoint,
    so it ends up doing close to a full placement anyway. Not investigated further (e.g. whether a
    stricter/different incremental-compile mode, or `write_checkpoint -cell`-level granularity,
    would do better) - diminishing returns for the effort already spent, and this alone wouldn't
    have addressed the synthesis-side cascade either.

**Where this leaves the whole investigation**: every readily-actionable lever has now been tried
and measured with real Vivado data. Summary of the actual cost breakdown for a full rebuild with
nothing logically changed (~51-55 minutes total): ~12 minutes synthesis (all 30+ sub-runs, run in
parallel via `-jobs 8` - genuinely redone every time, `launch_runs` has no skip-if-unchanged
behavior for this project structure in any tested configuration) + ~9 minutes place & route
(likewise always redone in full, incremental checkpointing doesn't help) + the remainder in
bitstream generation, reporting, and the SDK/ELF export steps. The Gradle-level fix (11s no-op when
*nothing* Gradle-tracked changed) remains the one clean, working win. Getting below the ~51-55
minute floor for a genuine application switch would require the full checkpoint/blackbox rewrite
discussed with the user - and even that would only remove the ~12 minute synthesis portion (based
on everything measured here), not the larger ~9+ minute place-and-route-and-beyond portion, since
incremental placement reuse has now also been shown not to work in this configuration. Realistic
expected improvement from the big rewrite, if it works at all: roughly 51-55 min -> ~40-43 min for
an application switch, not a total elimination of the wait.

**Update - Stage A (the synthesis-side rewrite) implemented, landed, and validated (commit
`4a15b8a`)**. The actual root cause turned out simpler than the checkpoint/blackbox design
originally scoped above: the real build entrypoint (`make -C $NF_DESIGN_DIR`, invoked by Gradle's
`makeBuild`) resolves to the project Makefile's `all: clean` target, which unconditionally deletes
`hw/project/` before every real build - so Vivado's own Design Runs staleness tracking never got a
chance to help, because there was never a persistent project for it to apply to. Confirmed directly
against real Vivado that once `hw/project/` is left in place, `synth_1`'s completed status persists
correctly across separate `vivado -mode batch` sessions, and a single sub-run
(`gapl_kernel_ip_synth_1`) can be `reset_run`/`launch_runs`/`wait_on_run`ed in complete isolation
without disturbing `synth_1`'s own status - `impl_1`'s `link_design` step then picks up the
refreshed sub-run checkpoint automatically with zero unresolved black-box warnings. No manual
`update_design -black_box`/`read_checkpoint -cell` choreography needed in production.

Fix: split Gradle's `makeBuild` into `makeSynthShell` (static-shell-only inputs, calls
`make -C hw create_project run_synth`) and `makeBuild` (app-specific inputs, calls
`make -C hw run_impl export_to_sdk` + `make -C sw project` + `make -C hw load_elf` - bypassing the
project Makefile's `all`/`clean` entirely). **Measured result: two real, different-app switches
(md5->regex, regex->cms) completed in 39m6s and 40m49s**, vs the ~51-55 min baseline - close to the
~40-43 min prediction above. Three real bugs found and fixed along the way (see
`netfpga/packet-processor/projects/reference_switch/hw/tcl/run_impl.tcl` and `hw/Makefile` comments
for full detail): `wait_on_run` doesn't throw on a failed run (added explicit `PROGRESS` checks);
Gradle pre-creates a task's declared output directory before running its action, which broke
`create_project`'s naive `test -d project/` existence check (fixed to check for the actual `.xpr`
file); and refreshing `identifier_ip`'s cached IP output products broke Vivado elaboration outright
(`module 'identifier_ip' not found`) - reverted, its build-timestamp ROM is now deliberately left
unrefreshed after initial project creation (cosmetic, not load-bearing).

**Update - Stage B (pblock reservation + `lock_design` for place & route) tried against real
Vivado, negative result, not pursued further**. Re-tested `incremental_checkpoint` first under
Stage A's now-genuinely-stable shell (on the theory it might now help, since the "previous routed"
reference finally matches a real prior build) - still no benefit (`place_design` 4m35s vs 4m34s
baseline, identical; Vivado logs "Incremental flow is disabled" in both). Then built the full
pblock design: sized a pblock to `gapl_kernel_ip`'s real footprint (8354 LUTs, 1121 FFs, 8 BRAM
tiles, 0 DSPs - tiny relative to the device), `lock_design`d the other 129,895 static cells,
left `gapl_kernel_ip`'s 14,429 cells free, then blackboxed/reinjected a genuinely different
application's fresh netlist and ran real `opt_design`/`place_design`/`route_design`. Vivado's log
confirmed the locking was recognized and engaged incremental placement
(`Running Incremental Placer in ECO mode for unplaced cells`, `High Reuse Mode`) - but wall-clock
showed **no improvement**: `place_design` 4m17s (vs 4m34s/4m35s baseline, essentially unchanged),
`route_design` 6m10s (vs 4m09s baseline - *slower*). Likely explanation: `place_design`/
`route_design`'s fixed costs (global floorplanning, timing/congestion analysis, device-wide
legalization) scale with total design complexity, not with how many cells are actually free to
move - locking ~90% of a large, densely interconnected design doesn't proportionally shrink
runtime the way skipping ~30 independent synthesis sub-runs did for Stage A. Not pursuing further
for now; if revisited, Vivado's actual Dynamic Function eXchange (DFX)/reconfigurable-partition
flow (this license does include `PartialReconfiguration`, confirmed in `~/.Xilinx/Xilinx.lic`) is
the more promising next avenue over further `lock_design` tuning, but is a materially bigger,
more specialized effort - not a quick follow-up test using the same mechanisms already tried.
Stage A's ~12-16 min savings stands as the landed, validated result.

**Update - parallelization tested against real Vivado on a 24-core/32GB machine (`prague`),
mixed result: per-build core scaling is a bust, but running builds concurrently is a genuine win.**
First confirmed the Vivado license (`Vivado_ML_Enterprise_Edition`, includes `PartialReconfiguration`)
is actually valid on this machine - its `HOSTID=7085c295efd3` matches this machine's own MAC
(`70:85:c2:95:ef:d3`, `ip link show`), and a real `create_project` succeeded under both the
installed 2024.2 and the project's actual 2020.1 toolchain with no license error.

*Hypothesis 1 (full-shell synthesis scales with `-jobs`) - refuted, root cause identified.* Ran a
genuine cold `makeSynthShell` (fresh project, ~35 sub-runs + top-level `synth_1`, all launched
together) end to end: 19m57s total, `wait_on_run synth_1` itself 11m51s at the default
`-jobs 24` (`JOBS=$(shell nproc)` in `hw/Makefile` already auto-detects core count, no code change
needed to raise it). But per-run timestamps in `project/*.runs/*/runme.log` showed all ~35 OOC
sub-runs (`control_sub_*`, `gapl_kernel_ip_synth_1`, `identifier_ip_synth_1`) launched at the same
instant and *finished within a few minutes* - they were never the bottleneck. The **single
top-level `synth_1` run** (the main datapath's own RTL elaboration/optimization, a serial process
not split across `-jobs`) ran the entire 11m51s by itself. Confirmed by resetting all runs and
relaunching the full batch twice more, warm, varying only `-jobs`: `-jobs 8` -> 8m36s, `-jobs 24` ->
7m41s - a real but modest ~11% difference, not the multiple-x speedup "~30 independent sub-runs"
would predict, because most of that time is the one thing `-jobs` can't parallelize. (A synth_1-only
reset+relaunch, with no other runs queued, showed *zero* difference between `-jobs 8` and
`-jobs 24`, as expected - `-jobs` only matters when multiple runs are actually queued together.)
Side finding: this warm-project run_synth time (~7-8 min) is well under the ~12-16 min figure from
the original 8-core/7.6GB machine, but that's not attributable to `-jobs`/core count per the above -
likely just less memory pressure or a faster CPU, not investigated further.

*Hypothesis 2 (single build's place & route scales with more cores) - refuted directly.* `-jobs` on
`launch_runs` doesn't control a single run's internal thread count at all (it only governs how many
separate runs execute concurrently) - the actual lever is `set_param general.maxThreads`. Explicitly
capped to 8 vs 24 on the same warm project, same design: `place_design` 3m03s vs 3m14s,
`route_design` 2m16s vs 2m11s - statistically identical, confirming place & route in this Vivado
version doesn't scale past ~8 threads regardless of what's available, matching the doc's own prior
caution from the Stage B pblock testing. (Absolute times here, ~3 min place / ~2 min route, are well
under the original machine's 4m34s/4m09s baseline - real, but again a machine/memory-pressure effect,
not a core-count effect, since capping to 8 threads reproduced it exactly.)

*Hypothesis 3 (concurrent multi-build throughput) - confirmed, clean win.* Duplicated the completed
project directory (`cp -r project project_copy2`, 528MB) and ran two `impl_1` (place & route)
sub-processes concurrently, each explicitly capped to `general.maxThreads 6` (12 threads total,
leaving headroom on 24 cores) with ~26GB memory available. Both finished with **individually
identical** timing to a solo run (`place_design` ~3m02s/3m03s, `route_design` ~2m18s/2m19s,
`write_bitstream` ~0m50s/0m51s each) and the combined wall-clock for *both* running together was
11m32s - essentially the same as one solo run's total time. Genuine ~2x throughput for free, with
zero measured slowdown to either build, when per-build thread counts are explicitly capped rather
than left to Vivado's default auto-detection (which would have both builds independently try to
grab all 24 cores and oversubscribe). Caveat: this machine is also a live interactive desktop, not
a dedicated build box - available memory swung from 9.6GB to 26GB across this session depending on
what else (Firefox, KDE) was running, so the safe concurrency level in practice depends on that
headroom at build time, not just core count; budget per-build memory (~5.5-5.8GB peak RSS observed
here) and thread count (~6-8, past which a single build stops benefiting anyway per Hypothesis 2)
explicitly rather than assuming `nproc`-derived defaults are safe to run N-at-a-time.

**Practical implication for `netfpga/build.gradle.kts`**: raising the shell's default `-jobs` further
isn't worth doing (modest win, and the Makefile already auto-detects `nproc`). The one *actionable*
lever from this investigation is explicit concurrency - if there's ever a need to build multiple
GAPL applications' bitstreams in one sitting (e.g. regression-testing several apps), running them as
concurrent `vivado` invocations with an explicit `general.maxThreads` cap per build (~6-8) is a real,
measured ~2x-or-better throughput win over running them sequentially, not just a plausible theory -
but nothing in the current Gradle task graph does this today; `makeSynthShell`/`makeBuild` are
single-project, single-build tasks, and wiring up N-at-a-time concurrent builds (separate project
directories, capped thread counts, careful memory budgeting against whatever else the host machine
is doing) would be new work, not a config tweak.