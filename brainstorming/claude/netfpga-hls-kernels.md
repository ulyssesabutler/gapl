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
- **Then: `hls_wrapper.v` and Gradle dispatch.**
  - `hls_wrapper.v`.
  - A `generateHlsVerilog` / `installHlsVerilog` pair feeding `packageCoreGaplKernel`.
  - `gapl_kernel.tcl`'s `set top` and the wrapper's util-file list become per-kernel-type.
  - `kernel=` handling in `compile.properties`.
  - An `md5/hls-pipelined` variation.
- **Then: bring-up.** `runSimulation`, build, flash, and the traffic-generator test. Then collect
  resource and timing numbers against `md5/min-register-count` and `md5/per-port-min-register-count`.

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
