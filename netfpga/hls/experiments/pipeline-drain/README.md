# Pipeline-drain experiment

Answers one question before any real HLS kernel is written: **does an HLS-pipelined AXI-Stream
kernel release its last beats when input stops?** In the NetFPGA wrapper it must, because
`axis_mutual_exclusion` admits the next packet only after the current packet's output `tlast` has
come out. A pipeline that needs more input to push its last beats out deadlocks there.

`toy.cpp` is a 256-bit `ap_axiu` in/out kernel (`ap_ctrl_none`, `II=1`) running 16 MD5-like rounds
at a 4 ns target, so HLS has to pipeline it 34-47 stages deep. The upper 128 bits pass through as a
tag so testbenches can identify beats. It's built in five variants:

| Variant | Pipeline pragma / code |
|---|---|
| `STP` | `#pragma HLS PIPELINE II=1` (the default stall pipeline) |
| `FLP` | `... II=1 style=flp` |
| `FRP` | `... II=1 style=frp` |
| `FLUSH` | `... II=1 enable_flush` |
| `NB` | default pragma, but `src.read_nb()`, writing output only for a valid read: the kernel carries its own valid bit, like GAPL's `i.valid` |

## Running it

```
bash run_all.sh      # csynth every tool x variant, then xsim tb.v (3-beat burst, then idle)
bash stress_all.sh   # xsim tb_stress.v (200 beats, random gaps + random backpressure)
bash synth_all.sh    # Vivado 2020.1 OOC synth_design for xc7vx690t-3, 4 ns clock
```

Outputs go to `runs/<tool>/<variant>/` (gitignored). Tool paths are this machine's `/tools/Xilinx`
installs. Set `TOOLS_OVERRIDE` to run a subset of tools in `run_all.sh`.

## Results (2026-09-23)

| Tool | STP | FLP | FRP | FLUSH | NB |
|---|---|---|---|---|---|
| Vivado HLS 2020.1 | deadlock | option unknown, fails | option unknown, fails | drains, II=1 | drains, II=1 |
| Vitis HLS 2020.1 | deadlock | `style` ignored, pipeline dropped (II=48) | same as FLP | drains, II=1 | drains, II=1 |
| Vitis HLS 2024.2 | deadlock | drains, II=1 | reported "yes" but behaves as STP: deadlock | drains (deprecated alias for flp) | drains, II=1 |

- **Deadlock** means that in `tb.v`, 0 of 3 beats ever come out: the whole burst stays inside the
  pipeline. Under `tb_stress.v` those variants strand the last 33-46 beats, one pipeline-depth's
  worth. Every variant that drains in `tb.v` also passes `tb_stress.v` 200/200, in order.
- The csynth report's `Pipeline` column is the way to catch a silently ignored option:
  - Vitis HLS 2020.1 FLP/FRP show `none` with II=48.
  - 2024.2 FLP shows `yes(flp)`, but 2024.2 FRP shows plain `yes`.
- Vivado 2020.1 OOC synthesis, xc7vx690t-3, 4 ns clock: every variant has 0 errors and 0 critical
  warnings, including the 2024.2-generated RTL.

  | Tool / variant | LUTs | FFs | WNS (ns) |
  |---|---|---|---|
  | Vivado HLS 2020.1 NB | 2,744 | 6,408 | 1.926 |
  | Vitis HLS 2020.1 NB | 3,603 | 6,551 | 2.007 |
  | Vitis HLS 2024.2 NB | 3,793 | 6,033 | 2.007 |
  | Vitis HLS 2024.2 FLP | 2,309 | 19,239 | 2.007 |
  | Vivado HLS 2020.1 FLUSH | 3,003 | 9,071 | 1.180 |

**Conclusion:** use the `NB` pattern (non-blocking read, carry your own valid bit, default pipeline
style) for NetFPGA HLS kernels. It's the only one that drains in all three tools, and it costs about
3x fewer flip-flops than the built-in flushable pipeline. Never rely on the default style: it builds
cleanly with no warning and then deadlocks in the wrapper. Output from any of the three tools reads
into Vivado 2020.1 without trouble.
