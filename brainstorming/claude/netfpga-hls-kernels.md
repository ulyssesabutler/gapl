# NetFPGA HLS kernels: design and status

Canonical doc for the effort to build NetFPGA applications from HLS C++ as well as from GAPL, so the
same application (MD5 first) can be compared across both. It covers them running through the same
NetFPGA wrapper, the same test vectors, and the same Vivado 2020.1 build.

## Goal and first target

The first HLS kernel is **single-block MD5**, the HLS counterpart of `netfpga/src/md5/`:

- Each 256-bit beat is padded to one 512-bit block with a fixed 256-bit length, then hashed from the
  MD5 initial state.
- The digest goes in the top half of the output beat, with zeros below it.
- `keep`, `valid` and `last` pass through, so there's one output beat per input beat.

Like GAPL's `md5/min-register-count` variations, it's **fully unrolled and pipelined to a ~10 ns
clock**. There, GAPL's retiming pass places the registers; here, HLS's scheduler does (with
`II=1`). That gives a like-for-like comparison of flip-flops, LUTs, latency and slack. Single-block
means no state is carried between beats, so II=1 is legal. Streaming multi-block MD5 would force a
much larger II and is out of scope for now.

## Decisions

- **Layout.** Implementation sources sit at the application's top level, named by implementation:
  - `gapl-processor.gapl` (renamed from `processor.gapl` in `7b5e6f9`)
  - `hls-processor.cpp` / `hls-processor.h`

  Subdirectories always mean variations. `test.properties` is shared by every variation and
  implementation. A variation picks its implementation with `kernel=gapl|hls` in
  `compile.properties` (default `gapl`).
- **Shared HLS infrastructure** goes in `netfpga/hls/`: testbench, run script, AXI-Stream helpers.
- **The kernel slot.** The shell only knows the `gapl_kernel_ip` IP (VLNV `GAPL:GAPL:gapl_kernel:1.00`).
  Anything packaged under it with the same ports is a drop-in kernel. Since `7b5e6f9`, the kernel
  Verilog is "every `*.v` in `hw/hdl/kernel/`", installed with a `Sync`. That's read by
  `packageCoreGaplKernel`, `gapl_kernel.tcl` and `reference_switch_sim.tcl`, and excluded from
  `makeSynthShell`'s inputs. So a multi-file HLS kernel fits without further plumbing.
- **Native AXI-Stream kernel plus its own wrapper.** The HLS kernel has native AXI-Stream ports
  (`ap_axiu<256,0,0,0>`, `ap_ctrl_none`). A new `hls_wrapper.v` would expose the same outer ports as
  `gapl_wrapper.v`:
  - It keeps `axis_pad_output` and `axis_mutual_exclusion` (one packet at a time, reset between
    packets, the same as GAPL's).
  - It drops `processor_controller` (GAPL's `enable` gating) and `reverse_bytes`.

  HLS applies backpressure itself through `tready`.
- **Use only HLS's raw Verilog** (`syn/verilog/*.v`), never its packaged IP. The existing
  `gapl_kernel` packaging then applies unchanged, and the HLS version doesn't have to match Vivado
  2020.1 (verified below).
- **Deferred renames.** `gapl_kernel`, `packageCoreGaplKernel`, `installGaplVerilog` and friends
  keep their GAPL-specific names until both paths work. Renaming the IP touches several Tcl files
  for purely cosmetic gain.

## Status

- **Done: layout groundwork** (`7b5e6f9`, plus sim fix `88b71c2`).
  - Rename to `gapl-processor.gapl` and the `hw/hdl/kernel/` install directory.
  - Generated Verilog is byte-identical to before for four variations.
  - `runSimKernelTest`, `runKernelTest` and `runSimulation` pass.
- **Done: pipeline-drain experiment.** See `netfpga/hls/experiments/pipeline-drain/README.md`.
  - HLS's default stall pipeline **deadlocks** behind `axis_mutual_exclusion`: the last packet's
    beats never leave the pipeline once input stops.
  - `style=frp` silently behaves the same in Vitis HLS 2024.2.
  - Vitis HLS 2020.1 silently drops `style=` pragmas altogether.
  - The one pattern that works in all three installed tools (Vivado HLS 2020.1, Vitis HLS 2020.1,
    Vitis HLS 2024.2) is a **non-blocking `read_nb()` that carries its own valid bit**. It keeps
    II=1 and uses ~3x fewer flip-flops than `style=flp`.
  - All tools' RTL synthesizes cleanly in Vivado 2020.1 for xc7vx690t-3.
- **Done: single-block MD5 HLS kernel** (`netfpga/src/md5/hls-processor.{h,cpp}`), with shared
  infrastructure in `netfpga/hls/`:
  - `common/netfpga_axis.h`: the `nf_beat` type, plus the kernel contract (top
    `packet_body_processor`, ports `i`/`o`, GAPL byte order, `read_nb` only).
  - `common/test_properties.h`: a `test.properties` reader with `hexToBeats`-identical beat
    splitting.
  - `common/kernel_tb.cpp`: a generic C-sim/cosim testbench that checks every output beat's data,
    keep and last.
  - `common/drain_tb.v`: an RTL drain plus random-gaps/backpressure check.
  - `run_hls.tcl`: a generic, environment-driven Vitis HLS script.
  - `check_kernel.sh <app>`: runs all of the above, plus the drain testbench in Vivado 2020.1's
    xsim.

  Results:
  - `netfpga/hls/check_kernel.sh md5` passes C-sim, C/RTL cosim and the drain check (203/203
    beats).
  - Mutation checks confirmed the tests catch faults. A corrupted expected digest fails C-sim. A
    blocking `read()` kernel still passes C-sim but fails the drain check with 0/3 beats.
  - HLS scheduled the 64 unrolled rounds as a 48-cycle, II=1 pipeline at 10 ns.
- **First comparison (out-of-context `synth_design` only, not placed/routed).** Vivado 2020.1,
  xc7vx690t-3, 10 ns clock. Latency is from `runKernelTest` for GAPL and csynth for HLS.

  | md5 kernel | LUTs | FFs | WNS (ns) | Latency (cycles) |
  |---|---|---|---|---|
  | HLS (Vitis HLS 2024.2) | 8,686 | 8,407 | 5.945 | 48 |
  | GAPL `min-register-count` | 7,749 | 9,210 | 4.250 | 55 |
  | GAPL `per-port-min-register-count` | 7,749 | 9,210 | 4.250 | 55 |

  The two GAPL variations synthesize to identical numbers. Both implementations meet 10 ns with
  wide margins, which suggests both are pipelined deeper than 10 ns strictly needs. The HLS
  kernel's figures include its two AXI-Stream register slices (in and out). These are rough
  post-synthesis numbers, and the real comparison should come from the routed full design.
- **Done: `hls_wrapper.v` and Gradle dispatch.**
  - `hw/hdl/hls_wrapper.v` has `gapl_wrapper.v`'s outer ports, padder, `axis_mutual_exclusion` and
    `reverse_bytes`, but no `processor_controller`.
  - `compile.properties` takes `kernel=gapl|hls` (default `gapl`) and `hlsClockPeriodNs` (default
    `clockPeriodNs`).
  - New tasks: `generateHlsVerilog` (Vitis HLS csynth) and `runHlsKernelTest` (runs
    `check_kernel.sh`).
  - `installGaplVerilog` now installs whichever kernel type is selected. Its name is kept, per the
    deferred-renames decision.
  - `packageCoreGaplKernel` rebuilds the core's `hdl/` from scratch with the selected wrapper and
    its utilities, and passes the IP's top module to `gapl_kernel.tcl` as `KERNEL_WRAPPER_TOP`.
  - GAPL-only tasks (`generateGaplVerilog`, `buildKernelTest`/`runKernelTest`, `runSimKernelTest`)
    fail with a pointer to the HLS equivalent on an HLS variation, and vice versa.
  - New variation: `md5/hls-pipelined`.
- **Found and fixed on the way: stale kernel IP definitions in reused Vivado projects.**
  - Switching applications within one kernel type only ever changed file *contents*. A GAPL ↔ HLS
    switch changes the kernel IP's file *list*, which exposed three layered problems. Each made IP
    generation use the previous kernel's file list ("Failed to copy file ... it does not exist").
  - First, every packaging was `gapl_kernel` 1.0 revision 1. `gapl_kernel.tcl` now sets
    `core_revision` to a timestamp.
  - Second, the reused sim project's `update_ip_catalog` served a cached parse of the old
    `component.xml`. It now uses `-rebuild`.
  - Third, the hardware project's catalog is `hw/ip_repo/`, a copy of `lib/hw/` made only once at
    project creation. `run_impl.tcl` now refreshes that core's copy and rebuilds the catalog before
    `upgrade_ip`.
  - Also fixed a regression from `7b5e6f9`: `run_impl.tcl`'s force-copy of the packaged kernel HDL
    only copied top-level `hdl/*.v`, so it skipped `hdl/kernel/`. It now replaces the whole
    directory.
  - `reference_switch_sim.tcl` also now calls `upgrade_ip` and drops kernel files read by earlier
    runs.
  - Verified in simulation: md5/hls-pipelined → crc32/unretimed (GAPL) → md5/hls-pipelined in one
    reused sim project all pass `runSimulation`, with the right wrapper compiled each time. The
    `run_impl.tcl` side is only exercised by a real hardware build.
  - `runSimulation`'s `run.py` compares packet contents against `test.properties`'
    `testExpectedOutputs`, so this is an end-to-end digest check through the whole switch.
- **Done: hardware builds (bitstreams, routed timing).** Three `:netfpga:build` runs in one
  project: `md5/hls-pipelined` (fresh project), then `md5/min-register-count`, then
  `md5/hls-pipelined` again. The last is a pure application switch: `makeSynthShell` was
  UP-TO-DATE, and `run_impl.tcl` refreshed, upgraded and resynthesized the kernel IP.
  - **Every build met all timing constraints.** The design-wide worst slack (+0.097 ns setup,
    +0.015 ns hold) is in the static shell's PCIe `userclk1` domain and is identical for GAPL and
    HLS. Kernel numbers, measured on the routed checkpoint at 10 ns and including each kernel's
    wrapper (GAPL's also carries `processor_controller`'s two queues):

    | md5 kernel (routed) | LUTs | FFs | Worst slack into / out of kernel |
    |---|---|---|---|
    | HLS `hls-pipelined`, fresh project | 8,027 | 8,315 | +3.572 / +3.158 ns |
    | HLS `hls-pipelined`, after the GAPL build | 8,766 | 8,315 | +2.810 / +2.810 ns |
    | GAPL `min-register-count` | 8,373 | 10,314 | +2.078 / +2.078 ns |

  - The two HLS rows are the same RTL. The second build's placement was done incrementally from
    the GAPL build's routed checkpoint.
  - The worst kernel paths are each design's pipeline-advance signal fanning out as clock enables.
    For HLS that's the stall-control register (`ap_enable_reg_pp0_iter*`), the ~27k-fanout signal
    flagged earlier. For GAPL it's `processor_controller`'s queue state driving `enable`.
  - One more fix came from these builds. A *real* `upgrade_ip` (possible only since the
    `core_revision` fix) moves the kernel's `.xci` into `sources_1`, turning off its out-of-context
    synthesis and deleting `gapl_kernel_ip_synth_1`. `run_impl.tcl` now restores
    `generate_synth_checkpoint` after `upgrade_ip`, and recreates the run just before relaunching
    it if it's missing. The third build exercised exactly that path.
- **Next: on-board test.** The FPGA is on a separate server. Flash the bitstream there and run the
  traffic-generator test (`netfpga/README.md`). Then compare against
  `md5/per-port-min-register-count` too, and look at why both implementations meet 10 ns with
  this much slack.

## Open questions and things to check

- **Tool choice: decided, Vitis HLS 2024.2.**
  - It's invoked as `vitis-run --mode hls --tcl`, since 2024.2 warns that the `vitis_hls`
    executable is deprecated.
  - The NetFPGA build stays on Vivado 2020.1. That works because only raw RTL crosses over (see
    Decisions).
  - The pipeline-drain toy verified this at out-of-context synthesis. The MD5 kernel's
    `runSimulation` and full build are the real end-to-end confirmation.
  - The 2020.1 HLS tools remain the same-version fallback.
- **Byte order at the boundary.**
  - GAPL sees each beat byte-reversed (`reverse_bytes` in `gapl_wrapper.v`). The HLS kernel
    won't.
  - `test.properties` should mean the same thing at the wrapper's outer AXI-Stream boundary for
    both implementations.
  - Before writing the HLS testbench, confirm that `kernel-test/test.cpp`'s lane mapping matches
    that outer view.
- **Stall-signal fanout.** Vitis HLS 2024.2 estimated a fanout of ~27,500 for the toy's pipeline
  stall-control signal at 256 bits wide. It met timing at 4 ns post-synthesis in isolation, but
  it's worth watching in the full routed design.
- **Test infrastructure.**
  - `runSimulation` exits 0 even when the simulation fails: `nf_test.py` swallows `make sim`'s
    exit status. Check for the `PASS` lines.
  - Simulation runs leave untracked artifacts (`hw/project_sim/`, `test/*.axi`, ...) that
    `.gitignore` doesn't cover.
