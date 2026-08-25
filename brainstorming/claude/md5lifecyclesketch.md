# MD5 Lifecycle Sketch

A paper design for a streaming MD5 on the NetFPGA pipeline, written to test the layer-1 / layer-2 /
layer-3 decomposition before any implementation. The point is to hand-typecheck the pipeline and find
out what breaks.

Syntax follows `antlr/src/main/antlr/CST.g4`: generic parameters are ordinary parameters
(`T: interface`, `n: integer`, `f: A => B`), no `parameter` keyword, no `<>`. Anything marked **NEW**
is proposed syntax that doesn't exist yet.

## The layers

| layer | describes | status |
|---|---|---|
| 1 | spatial layout — which bits on which wires, for one beat | exists (`interface`) |
| 2 | temporal layout — how many beats make one value, what each means | **NEW** (`datatype`) |
| 3 | timing discipline — when a beat is accepted (`fire`) | **NEW** (port/function annotation) |

Layer 3's only job is to produce `fire`, and to guarantee layer-2 state cannot advance when `fire` is
low. Layer 2 never observes stalls — that invariance is what lets one layer-2 description lower two
ways.

## Layer 1: beats

```text
interface beat(bytes: integer) {
    data: wire[8 * bytes];
    keep: boolean[bytes];
}

interface axi_beat   beat(32)     // 256b, NetFPGA pipeline width
interface md5_beat   beat(64)     // 512b, may be partially valid
interface md5_block  wire[512]    // a padded block: always full, no keep
interface md5_digest wire[128]
```

No `valid`, no `last`. Compare `netfpga/src/md5/processor.gapl`'s `netfpga_packet_body`, which carries
both and passes them through by hand. `last` is derived by layer 2, `valid`/`ready` by layer 3. The
stdlib's hand-rolled `valid(T)`/`last(T)` wrappers stop being needed as *interfaces* — though both
come back below as the way pure functions observe lifecycle position.

## Layer 2: lifecycles — **NEW**

A regular expression over beat types:

```text
single(T)          // exactly one beat — the default when a bare interface appears
stream(T)          // one or more beats, bounded, carries a derived `last`
infinite(T)        // unbounded, no `last`
repeat(T, n)       // exactly n beats
seq { a: D; b: E } // concatenation; phases may have different beat interfaces
```

```text
datatype axi_stream stream(axi_beat)
datatype md5_stream stream(md5_beat)
datatype md5_blocks stream(md5_block)
```

**A bare interface in a lifecycle position means `single`.** So `i: word => o: word` already declares
a lifecycle and every existing signature in `processor.gapl` keeps its exact current text. The rule is
positional and worth stating carefully:

| position | bare interface means |
|---|---|
| function IO (`i: T => o: U`) | `single(T)` |
| function-typed parameter (`op: T => U`) | `single(T)` |
| record port (`data: wire[256];`) | layer-1 interface, *not* a lifecycle |

`single(T)` means **one beat, not one clock cycle** — under backpressure the value is held until
accepted.

**Bounded vs. unbounded is explicit**, because configuration scope is otherwise inexpressible:

```text
needle: single(string), haystacks: infinite(stream(character))   // one needle for the whole run
needle: single(string), haystack:  stream(character)             // one needle per haystack
```

Two consequences: `fold` requires a *bounded* stream (an `infinite` one never reaches `last`, so a
fold over it never emits — a type error), while `scan` works over both. That gives CMS's persistent
sketch a real type: a `scan` over an infinite stream. And `single` over an infinite lifetime lowers to
a **sticky latch** — it participates in the join until it first arrives, then latches and drops out,
behaving as `@dataflow` from then on.

## Layer 3 — **NEW**

Two protocols only.

| | producer may stall | consumer may stall |
|---|---|---|
| `@dataflow` | no | no |
| `@ready_valid` | yes | yes |

`@valid` (producer-only stalling) is deliberately excluded from the surface language: every one of its
correctness properties is an unchecked obligation whose violation is silently dropped data. It survives
only in the NetFPGA boundary wrapper, written once. The fourth cell (consumer-only stalling) is
degenerate.

**The annotation goes on the function, and a call site may coerce.**

```text
function f()@dataflow    i1: wire, i2: wire => o: wire { ... }

function g()@ready_valid i1: wire, i2: wire => o: wire {
    i1, i2 => f()@ready_valid => o;   // coercion: this becomes an island, with a join before it
}
```

Rules:

- **Coercion is mandatory where protocols differ.** `i1, i2 => f() => o;` is a type error. That error
  is the entire enforcement mechanism — without it, islands get created invisibly and their cost
  becomes unreadable again.
- **Upgrade only.** `@dataflow => @ready_valid` is always sound. The reverse requires proving the
  function never stalls, so it's illegal.
- One definition, many protocols: `add` is written once as `@dataflow` and instantiated as
  `add()@dataflow` inside a datapath or `add()@ready_valid` at a boundary.

## Islands

A coercion site is an **island**: a region that freezes as a unit. Its boundary is a function boundary,
which matters because the out-coercion needs uniform latency across all paths, and
`MinimalRegisterSolver`'s `VirtualIONode` lag constraint already gives exactly that per module.

- **In-coercion** (`@ready_valid → @dataflow`) is the **join**: `fire = (∧ valid_i) && ready_out`,
  `ready_i = fire`. Its arity is the coerced function's input port count — visible in the signature.
- **Out-coercion** (`@dataflow → @ready_valid`) is `valid_out = delay(fire, latency)`, plus
  `enable = fire` on every register in the island.

**Islands are rate-preserving by construction**, and this enforces itself: a `@dataflow` function
cannot contain a `fold` or a `regroup`, because those are lifecycle-aware and need a handshake. So
`valid_out = delay(fire, latency)` is always correct without a separate check.

Lowering: `enable` becomes a port on every generated module, threaded down the hierarchy, and is
applied at **serialization** — every `always @(posedge clk)` becomes `if (enable)`. The netlist IR never
models it, so `Retimer`, `CombinationalLoopDetector`, and the graph converters keep working unchanged.
`valid`/`ready`/`last` *do* become real netlist wires, and must be materialized **before** the
transformer pipeline so the lag constraint keeps them aligned with the data they qualify.

This is what `processor_controller` in `gapl_wrapper.v` does today, hand-written once at the top level.

## Combinators

`scan` is the primitive; `map` and `fold` derive from it.

```text
// rate-preserving, protocol-polymorphic
function map(L: lifecycle, T: interface, U: interface, op: T => U)
    i: L(T) => o: L(U)

function scan(T: interface, U: interface, S: interface,
              step: last(T), S => U, S,
              init: null => S)
    i: stream(T) => o: stream(U)

// rate-reducing, @ready_valid only (output is inherently intermittent)
function fold(T: interface, A: interface,
              step: T, A => A,
              init: null => A)
    i: stream(T) => o: single(A)

function regroup(in_bytes: integer, out_bytes: integer)
    i: stream(beat(in_bytes)) => o: stream(beat(out_bytes))

// stateful transform with an optional trailing beat
function scan_with_tail(T: interface, U: interface, S: interface,
                        step: last(T), S => U, S,
                        tail: S => valid(U),
                        init: null => S)
    i: stream(T) => o: stream(U)
```

Every function parameter is **pure `@dataflow`** — none sees `valid`, `ready`, or `enable`. Lifecycle
position reaches them as *data*, via the stdlib's existing `last(T)` and `valid(U)` record interfaces,
never as control. That's the whole ergonomic bet.

`map` requires a combinational `op`. Non-zero latency enters only through hard macros (BRAM, DSP,
vendor IP), which are latency-declaring channel functions rather than lifted pure functions.

`map` is currently overloaded: beat-wise (combinational `op`, generates logic) and per-lifecycle
(`op` is a whole pipeline, generates *nothing* — a module already is a standing process). Different
hardware meanings, same name for now; see Open questions.

## The pipeline

```text
function md5_application()@ready_valid
    i: infinite(stream(axi_beat)) => o: infinite(stream(axi_beat))
{
    i => map(md5_packet) => o;
}

function md5_packet()@ready_valid i: stream(axi_beat) => o: stream(axi_beat)
{
    i => regroup(32, 64)              // stream(axi_beat)  => stream(md5_beat)
      => md5_pad()                    // stream(md5_beat)  => stream(md5_block)
      => md5_core()                   // stream(md5_block) => single(md5_digest)
      => md5_digest_to_axi()@ready_valid
      => o;                           // single(axi_beat) widens to stream(axi_beat)
}

function md5_core()@ready_valid i: md5_blocks => o: md5_digest
{
    i => fold(md5_block, md5_state, md5_step, md5_initial_state)
      => md5_hash_output_from_state()@ready_valid
      => o;
}

function md5_step()@dataflow i: md5_block, s: md5_state => o: md5_state
{
    i        => md5_hash_block_from_input() => declare block: md5_input_block;
    block, s => md5_hash_block()            => o;
}

function md5_initial_state()@dataflow null => o: md5_state
{
    literal(32, 1732584193) => o.a;
    literal(32, 4023233417) => o.b;
    literal(32, 2562383102) => o.c;
    literal(32, 271733878)  => o.d;
}

function md5_digest_to_axi()@dataflow i: md5_digest => o: axi_beat
{
    i               => o.data[128:255];
    literal(128, 0) => o.data[0:127];
    // o.keep: bytes 16..31 set
}
```

Note the calls *inside* `md5_step` carry no annotation — both callees are `@dataflow`, so protocols
match and no coercion is needed. That's the common case, and it's why the mandatory-coercion rule
costs almost nothing in practice.

**Everything from `md5_hash_block` down is reused verbatim** — `md5_hash_block_from_iteration`,
`md5_iteration`, `k_table`, `left_rotate_for_iteration`, `left_rotate_by`, `reverse_endianess`,
`add_words`, `md5_hash_block_from_input`, `md5_hash_output_from_state`. Not one line changes. They just
need `@dataflow` on their signatures.

`md5_hash_block(i: md5_input_block, input_state: md5_state) => output_state: md5_state` was already a
fold step function, feed-forward and all. The only casualty is `md5_single_round_hash`, whose sole job
was hardcoding the IV; its body becomes `md5_initial_state` and the chaining becomes `fold`'s
accumulator.

## `md5_pad` — three ways

The requirement: append `0x80` after the last message byte, zero-fill, and put the 64-bit little-endian
bit length in the final 8 bytes. If the last message block has ≥ 56 bytes occupied there's no room, and
a whole extra block is needed. So output length is |in| or |in|+1, data-dependently.

Causality is favorable: end position is known at `last`, total length is a running count, the length
field goes last. No lookahead, no whole-message buffer.

**Attempt A — four-parameter combinator** (`step`, `finalize`, `tail`, `init`). Works, but `step` and
`finalize` duplicate most of their logic, and four function parameters is a lot of surface.

**Attempt B — `scan` then a separate `append`.** Fails compositionally: `append` needs the byte count,
which lives in `scan`'s state and isn't exposed after the lifecycle ends. Forking the stream to `fold`
for the length in parallel deadlocks — the fold's result arrives at the last beat, which is exactly
when the scan needs it.

**Attempt C — Tier 2 / guarded emit.** Reads most naturally, but needs a guarded-action mechanism
("emit this beat") that hasn't been designed.

**Attempt D — chosen.** Collapse `step`/`finalize` by passing lifecycle position as *data*, and make the
tail optional via the existing `valid(T)` interface:

```text
function md5_pad()@ready_valid i: md5_stream => o: md5_blocks
{
    i => scan_with_tail(md5_beat, md5_block, wire[64],
                        md5_pad_step, md5_pad_tail, md5_zero_count)
      => o;
}

function md5_pad_step()@dataflow
    b: last(md5_beat), count: wire[64] => block: md5_block, next: wire[64]
{
    // b.last distinguishes the final beat; b.value.keep gives the byte count.
    // Non-final: pass data through, next = count + 512.
    // Final:     insert 0x80 after the kept bytes, zero-fill, and if
    //            (keep_bytes <= 55) place next in bytes 56..63.
}

function md5_pad_tail()@dataflow count: wire[64] => extra: valid(md5_block)
{
    // extra.valid = (count % 512) >= 448   — no room for 0x80 + length
    // extra.value = zeros with count in the final 8 bytes
}

function md5_zero_count()@dataflow null => o: wire[64] { literal(64, 0) => o; }
```

Three function parameters, all pure, all `@dataflow`, and both `last(T)` and `valid(U)` already exist
in `StandardLibrary.kt`. **This closes the epilogue gap.**

## Hand typecheck

Let `N` = beats in one packet body, `M = ceil(N/2)`.

| stage | type | beats | lifecycle |
|---|---|---|---|
| `i` | `stream(axi_beat)` | N | 1 |
| `regroup(32, 64)` | `stream(md5_beat)` | M | 1 |
| `md5_pad()` | `stream(md5_block)` | M or M+1 | 1 |
| `fold(...)` | `single(md5_state)` | 1 | 1 |
| `md5_hash_output_from_state()` | `single(md5_digest)` | 1 | 1 |
| `md5_digest_to_axi()` | `single(axi_beat)` | 1 | 1 |
| widen | `stream(axi_beat)` | 1 | 1 |

One lifecycle in, one out, end to end.

Islands, and their join arity: `md5_step` (2 inputs, joined inside `fold`), `md5_hash_output_from_state`
(1, trivial), `md5_digest_to_axi` (1, trivial), plus the three pure functions inside `scan_with_tail`.
No join wider than 2 anywhere in the design.

## What the sketch found

### 1. `md5_stream` was under-specified

`md5_pad` needs the message end to the byte and the total bit length. `axi_beat` carries that in
`keep`; if `md5_beat` were only 512 bits of data, the information dies in the gearbox. Hence
`beat(bytes)` carrying `keep` at both widths, with `md5_block` a distinct always-full type. Under the
one-lifecycle-in/one-out invariant this is a type error rather than a silent bug.

### 2. Padding is not structurally interesting

An earlier framing had padding producing `seq { message; footer }`. Wrong for MD5 — the padding starts
mid-beat and the output blocks are homogeneous; only the *count* is data-dependent. `seq` is not needed
anywhere in this application.

### 3. The serious one: `fold` creates a loop retiming cannot pipeline

Today's design is feed-forward — `md5_hash_block_from_iteration` statically recurses 64 deep, the whole
hash is combinational, and there is not one `register` in the file. Retiming inserts as many stages as
it likes, which is what the configuration matrix under `netfpga/src/md5/` exercises.

The streaming design puts the chaining value in a register:

```text
state_reg -> md5_step (64 unrolled rounds) -> state_reg
```

A cycle containing exactly one register. Leiserson–Saxe preserves total register count around any cycle
(the lag differences telescope to zero), so **no retiming can pipeline it**. The clock period is floored
at the full 64-round combinational delay.

This is inherent to MD5 chaining, not to the type system. But it's a concrete instance of the
"efficiency of structured layer 2" worry, and worth knowing before building.

Mitigations, in order of attractiveness:

- **C-slow retiming.** Interleave C independent packets so the loop carries C registers, then retime to
  C stages. Successive packets on a NetFPGA pipeline are independent, so it genuinely applies. It is a
  transformation the compiler doesn't have (C-slowing multiplies registers around cycles first, then
  retimes), but it builds on the existing `HierarchicalLeisersonCircuitGraph` machinery. **It is
  currently impossible by construction** — see finding 5.
- **Multi-cycle compression.** Fewer than 64 rounds per cycle, trading throughput for clock period.
  Changes the rate, so layer 3 sees it.
- **Accept the lower Fmax.** Measurable today against the existing retimed builds.

`fold` should report that it closes a combinational cycle through its step function;
`findMinimumClockPeriod` already computes the floor.

### 4. The parameter system is on the critical path

`regroup(32, 64)` needs byte-count arithmetic at compile time; `beat(bytes)` needs `8 * bytes`. Both
land on the static-expression system, whose type layer is `SignatureGenericParameterType` — one
`Integer` object and two `// TODO`s. The existing MD5 already leans on this machinery hard (64-deep
static recursion, `(5 * iteration + 1) % 16`, static `if` chains), so it works, but layer-2 tooling
makes the dependency structural rather than incidental.

### 5. The wrapper currently serializes packets, and that's the bigger throughput story

`axis_mutual_exclusion` is a three-state FSM — `INGRESSING → EGRESSING → RESETTING` — whose
`module_reset` output drives the kernel's `reset`. The kernel handles **one packet at a time, with a
full module reset between packets**. That is how per-lifecycle state is managed today: externally, by
resetting everything.

So there is no packet-level pipelining at all. For MD5 retimed to *k* stages, you pay *k* cycles of
drain per packet instead of amortizing it; on minimum-size Ethernet frames the drain dominates the
actual work.

`scan`/`fold`'s automatic per-lifecycle reset replaces `module_reset` — precisely, resetting only the
accumulator instead of the whole kernel. That removes the need for mutual exclusion, which unlocks
packet-level pipelining, which is *also* the precondition for the C-slow fix in finding 3.

(Read: the port list, state declarations, and the `INGRESSING` combinational block. The exact
transition conditions and `processor_controller` were not traced, so the precise drain cost is
unquantified.)

## Open questions

- **`map` is overloaded** — beat-wise (generates logic) vs. per-lifecycle (generates nothing).
  Deliberately deferred; needs distinct names eventually.
- **Rate relationships between two ports have no syntax.** Blocks two-stream joins, `zip`, and any
  `regroup` whose ratio isn't statically known. Doesn't block MD5. Probably the highest-priority
  remaining gap in the lifecycle language.
- **`enable` is not `reset`**, and after this change they sit on the same registers. A stalled cycle is
  not a lifecycle boundary; cross-wiring them would be a subtle bug.
- **Enable fanout.** A function-sized island puts its whole register set on one net. Fine at MD5's
  scale, a placement problem later.
- **One stall domain per island.** A function with two outputs consumed at genuinely different rates
  can't be one island. Worth a diagnostic rather than a silently under-performing design.
- **Memories need their own serializer rule** when BRAM primitives arrive — "gate every register" does
  not extend correctly to BRAM read/write enables.
- **`CombinationalLoopDetector` becomes the elastic-deadlock detector** once `ready` is a real netlist
  wire, since forks and joins can close a cycle through it. Correct behaviour, but the diagnostic will
  point at compiler-generated nodes in a flattened graph. Budget a good error message; this is the bill
  for deferring wire sorts.
- **`zip` on unequal-length streams**: no static check, no hardware error handling — but implement it to
  **deadlock** (require both `last`s to coincide) rather than desynchronize, and assert in `simengine`.
  Deadlock is loud and debuggable; desync corrupts every subsequent lifecycle.
