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