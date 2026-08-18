# NetFPGA partial synthesis: status and next steps

Canonical status doc for the effort to make NetFPGA hardware builds fast when only the selected
GAPL application changes (not the static NetFPGA infrastructure around it). For the full
chronological investigation log - every real-Vivado test, dead end, and piece of reasoning behind
the decisions below - see `brainstorming/todo.md`. This doc is the entry point; that one is the
backing detail.

## The problem

A NetFPGA build (`:netfpga:makeBuild`) compiles a GAPL application, synthesizes it alongside a
large, mostly-fixed pile of static infrastructure (PCIe/DMA/MicroBlaze control subsystem, ~20
packaged NetFPGA IP cores, routing/switching datapath logic), places and routes the whole design,
and produces a bitstream plus embedded software. A full "nothing logically changed" rebuild costs
**~51-55 minutes**. Before this work, switching *only* the GAPL application (the common iteration
loop) cost the same ~51-55 minutes, because nothing distinguished "the static shell changed" from
"just the application changed."

## Status: Phase 1 - landed (Gradle-level caching and correctness fixes)

Commits: `c2c5d8d` through `d886d50` (see `git log --oneline` for the full list; `brainstorming/todo.md`
has the detailed trail).

- Made IP-core packaging (~20 std/contrib NetFPGA cores) genuinely Gradle-incremental, with a real
  dependency graph instead of a linear chain, so packaging only reruns what actually changed.
- Fixed a bug where repeated `runSimulation` runs eventually failed on an `axis_sim_pkg` copy
  collision, and another where sim clobbered synth state and vice versa.
- Packaged the GAPL kernel and `identifier_ip` as checkpointed Vivado IPs (`generate_synth_checkpoint`
  enabled), isolating their own resynthesis from the rest of the design.
- Fixed a silent bug where switching GAPL applications on an already-created project would keep
  synthesizing whichever application was selected when the project was first created.
- Gave `:netfpga:makeBuild` real Gradle `inputs`/`outputs`, achieving a true ~11s no-op when nothing
  Gradle-tracked changed at all - the one clean win from this phase alone.

## Status: Stage A - landed and validated (commits `4a15b8a`, `586901a` + supporting fixes)

**The real root cause** turned out simpler than initially assumed: the actual build entrypoint
(`make -C $NF_DESIGN_DIR`, called by Gradle's `makeBuild`) resolves to the project Makefile's
`all: clean` target, which unconditionally deletes `hw/project/` before every real build. Vivado's
own Design Runs staleness tracking was never the problem - there was simply never a persistent
project for it to apply to. Confirmed directly against real Vivado that once `hw/project/` is left
in place, `synth_1`'s completed status persists correctly across separate `vivado -mode batch`
sessions, and a single sub-run (`gapl_kernel_ip_synth_1`) can be reset/relaunched in complete
isolation without disturbing `synth_1`'s own status - `impl_1`'s `link_design` step then picks up
the refreshed sub-run checkpoint automatically, with zero unresolved black-box warnings.

**The fix**: split Gradle's `makeBuild` into two tasks (`netfpga/build.gradle.kts`):
- `makeSynthShell` - inputs scoped to the *static* shell only (excludes the per-application
  `GAPLprocessor.v`); calls `make -C hw create_project run_synth` directly, bypassing the project
  Makefile's `all`/`clean`. Only reruns (~12-16 min) when the static shell actually changed.
- `makeBuild` - inputs include the GAPL kernel's `component.xml` (app-specific); depends on
  `makeSynthShell`; calls `make -C hw run_impl export_to_sdk` + `make -C sw project` +
  `make -C hw load_elf`. Always runs on an application switch, but no longer pays the ~12 min
  synthesis cost.

**Measured result**: two real, different-application switches (`md5`→`regex`, `regex`→`cms`)
completed in **39m6s and 40m49s**, down from the ~51-55 min baseline.

**Three real bugs found and fixed during validation** (see comments in `run_impl.tcl` and
`hw/Makefile` for full detail - worth reading before touching this code again):
1. `wait_on_run` does not throw on a failed Vivado run - it just returns, so a failed build could
   silently report success. Fixed with an explicit `PROGRESS` check after every `wait_on_run`.
2. Gradle pre-creates a task's declared output directory before running its action, which broke
   the vendored `create_project` Makefile target's naive `test -d project/` existence check (an
   empty, Gradle-created directory looked like an existing project). Fixed to check for the actual
   `.xpr` file instead.
3. An attempt to keep `identifier_ip`'s embedded build-timestamp ROM fresh on every build (mirroring
   how `gapl_kernel_ip` is refreshed) broke Vivado elaboration outright. Reverted; that timestamp is
   now deliberately left stale after initial project creation - it's informational only, not
   load-bearing for GAPL functionality.

## Status: Stage B - tried against real Vivado, negative result, not pursued further

Idea: reserve a Vivado `pblock` for `gapl_kernel_ip`'s cells, `lock_design` everything else, and
reuse that locked, placed-and-routed shell across application switches so `place_design`/
`route_design` only have to work on the small unlocked region - addressing the ~9 min place & route
cost that Stage A doesn't touch.

Built and tested the full mechanism against a real routed checkpoint: sized a pblock to
`gapl_kernel_ip`'s actual footprint (8354 LUTs, 1121 FFs, 8 BRAM tiles, 0 DSPs - tiny relative to
the device), locked the other 129,895 static cells, left `gapl_kernel_ip`'s 14,429 cells free,
blackboxed and reinjected a genuinely different application's fresh netlist, then ran real
`opt_design`/`place_design`/`route_design`. Vivado's own log confirmed the lock was recognized and
engaged incremental placement (`Running Incremental Placer in ECO mode for unplaced cells`, `High
Reuse Mode`) - but wall-clock showed **no improvement**: `place_design` 4m17s (vs a 4m34s/4m35s
full-device baseline - unchanged), `route_design` 6m10s (vs a 4m09s baseline - *slower*).

Likely explanation: `place_design`/`route_design`'s fixed costs (global floorplanning, timing and
congestion analysis, device-wide legalization) scale with total design complexity, not with how
many cells are actually free to move. Locking ~90% of a large, densely interconnected design
doesn't proportionally shrink runtime the way skipping ~30 independent synthesis sub-runs did for
Stage A, where the skipped work was genuinely separable.

Also re-tested Vivado's `incremental_checkpoint` mechanism (already wired into `run_impl.tcl` from
Phase 1, previously tested negative) under Stage A's now-genuinely-stable shell, on the theory it
might now help since the "previous routed" reference finally matches a real prior build. Still no
benefit (`place_design` 4m35s vs 4m34s, identical; Vivado logs "Incremental flow is disabled" in
both).

**Not pursuing further for now.** If revisited, Vivado's actual Dynamic Function eXchange (DFX) /
reconfigurable-partition flow is the more promising next avenue over further `lock_design` tuning -
it's built for exactly this "static shell + swappable region" scenario, and the installed license
does include the `PartialReconfiguration` component (confirmed in `~/.Xilinx/Xilinx.lic`). But it's
a materially bigger, more specialized effort requiring a real redesign around reconfigurable
partitions - not a quick follow-up test using the mechanisms already tried.

**Stage A's ~12-16 min savings stands as the landed, validated result** (~51-55 min → ~39-41 min
for a genuine application switch).

## Status: parallelization on a 24-core/32GB machine - tested against real Vivado, mixed result

License first confirmed genuinely valid on the new machine (`prague`, 24 cores/32GB/64GB swap) -
its `HOSTID=7085c295efd3` matches the machine's own MAC (`70:85:c2:95:ef:d3`), not just a copied
file, and a real `create_project` succeeded under both installed Vivado versions.

- **Full-shell synthesis scaling with `-jobs` - refuted.** The ~30 independent OOC sub-runs
  (`control_sub_*`, `gapl_kernel_ip_synth_1`, etc.) all launch together and finish within a few
  minutes regardless of `-jobs` - they were never the bottleneck. The single top-level `synth_1` run
  (the main datapath's own serial synthesis) dominates the ~7-12 min wall-clock and isn't split
  across `-jobs` at all. A controlled warm A/B (`-jobs 8` vs `-jobs 24`, all runs reset together)
  showed only ~11% difference (8m36s vs 7m41s), not a multiple-x speedup.
- **Single build's place & route scaling with more cores - refuted.** `-jobs` on `launch_runs`
  doesn't govern a single run's internal threading at all; that's `general.maxThreads`. Capped
  explicitly to 8 vs 24: `place_design` 3m03s vs 3m14s, `route_design` 2m16s vs 2m11s - statistically
  identical. Place & route in this Vivado version doesn't scale past ~8 threads, confirming the
  Stage B pblock testing's earlier observation.
- **Concurrent multi-build throughput - confirmed, the one clean win.** Two `impl_1` place & route
  runs launched simultaneously (separate project copies, each capped to `general.maxThreads 6`)
  finished with individually identical timing to a solo run, and combined wall-clock for both
  together (11m32s) matched one solo run's total time - genuine ~2x throughput for free, provided
  per-build thread count is explicitly capped (not left to Vivado's `nproc`-derived default, which
  would oversubscribe) and memory headroom is budgeted per build (~5.5-5.8GB peak RSS observed) -
  this machine is also a live interactive desktop, so available memory varies with what else is
  running, not just its 32GB nameplate figure.

No code changes landed from this round - it answers "is parallelization worth pursuing" rather than
implementing anything. The actionable takeaway: raising `-jobs` further isn't worth it (the
Makefile's `JOBS=$(shell nproc)` already gets the (small) available win automatically); the real
lever is running multiple full builds concurrently with explicit thread/memory budgeting, which
would need new Gradle/script work to wire up (nothing today drives N-at-a-time builds) and is only
worth doing if there's an actual use case for building several GAPL applications in one sitting
(e.g. regression-testing many apps) rather than the single-application iteration loop Stage A
already optimized. Full detail, numbers, and log excerpts: `brainstorming/todo.md`.

## Relevant source

- `netfpga/build.gradle.kts` - the `makeSynthShell`/`makeBuild` task split, all IP-core packaging
  tasks, Gradle-level incrementality.
- `netfpga/packet-processor/projects/reference_switch/hw/Makefile` - `create_project`'s `.xpr`
  existence check, `identifier`/`create_project`/`run_synth`/`run_impl`/`export_to_sdk`/`load_elf`
  targets.
- `netfpga/packet-processor/projects/reference_switch/Makefile` - the vendored `all`/`clean`
  entrypoint that Gradle now deliberately bypasses for iterative builds (still available for a
  guaranteed from-scratch build).
- `netfpga/packet-processor/projects/reference_switch/hw/tcl/{run_synth,run_impl,load_elf}.tcl` -
  the actual Vivado flow, including the `gapl_fail_if_run_failed` failure-propagation helper, the
  `gapl_kernel_ip`/`identifier_ip` refresh logic, and the (currently still wired up but
  ineffective) `incremental_checkpoint` logic.
