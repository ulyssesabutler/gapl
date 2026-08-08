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

## Next planned work: testing parallelization on a bigger machine

The original machine this work was done on has only 8 CPU cores and 7.6GB RAM - both already
close to saturated by a single Vivado build (`-jobs 8` matches `nproc`; place & route alone peaks
around 5GB RSS). A second machine is available with 24 cores, 32GB RAM, 64GB swap, using
(reportedly) the same Vivado license.

**Before relying on anything below: verify the license is actually valid on that machine.** The
license file inspected on the original machine (`~/.Xilinx/Xilinx.lic`) grants
`Vivado_ML_Enterprise_Edition` as **node-locked** (tied to that specific machine's `HOSTID`) and
marked `uncounted` (no concurrency limit enforced *by the license*, once it's valid on a given
host). "Same license" needs to mean a matching entry for the *new* machine's own hostid (or a
genuinely floating/network license), not literally the same file copied over - check
`~/.Xilinx/Xilinx.lic` (or wherever `XILINXD_LICENSE_FILE`/`LM_LICENSE_FILE` point) on the new
machine before assuming this works.

**Hypotheses to test, roughly in order of expected payoff** - treat these as hypotheses, not
conclusions; this whole investigation has repeatedly shown plausible Vivado theories (incremental
checkpointing, pblock locking) measuring flat or negative in practice, so nothing here should be
assumed without a real, timed build to confirm it:

1. **Full-shell synthesis (`makeSynthShell`, the rare ~12-16 min path)** should scale well with more
   cores. It launches ~30 independent OOC sub-run syntheses via `launch_runs -jobs N`
   (`run_synth.tcl`), which is genuinely embarrassingly parallel - on 8 cores those queue in
   batches; on 24 cores most could run simultaneously. Test: force a shell rebuild (e.g. touch
   something in `hw/hdl` excluding `GAPLprocessor.v`, or just do a truly clean build) with `-jobs`
   raised to match the new core count, and compare wall-clock against the ~12-16 min baseline.
2. **A single build's place & route (`run_impl.tcl`, the ~9 min dominant per-switch cost)** is a
   weaker bet. Vivado's core placement/routing algorithms don't scale much past ~8 threads in
   practice from what's been observed here, so going from 8 to 24 cores for *one* build's place &
   route will likely help some but probably not proportionally. Measure, don't assume.
3. **Running multiple full builds concurrently** (e.g., testing several GAPL applications in
   parallel) becomes resource-plausible with 32GB RAM (~5GB/instance observed here) and 24 cores,
   but only if each build's `-jobs`/thread count is explicitly capped (e.g., `-jobs 6` × 4 concurrent
   builds) - otherwise each build will try to grab all 24 cores by default via Vivado's own
   auto-detection, and you'll get CPU oversubscription instead of a speedup. This is the most
   speculative of the three and the one most worth a real timed test before investing further.

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
