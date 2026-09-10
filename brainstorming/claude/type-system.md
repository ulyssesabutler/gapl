# GAPL Type System — Design Notes

**Status:** design in progress, nothing implemented. Written to carry the reasoning across sessions.

This is the "why" behind `md5-lifecycle-sketch.md` (the worked example) and `applications.md` (the
roadmap). Read this one first.

> **Syntax warning.** `brainstorming/archive/interfaces.md` and several `gapl-example/wip/*.gapl` files
> use `<T>` generics and a `parameter` keyword that **do not match the actual grammar**. Real syntax, per
> `antlr/src/main/antlr/CST.g4`: generic parameters are ordinary parameters —
> `interface pair(T: interface, U: interface)`, `function repeat(I: interface, factor: integer, operation: I => I)`.
> No `<>`, no `parameter` keyword. Check `CST.g4` before trusting any GAPL syntax in a markdown file.

---

## 1. Why this exists

### Structure vs. behavior

From "A Typing Discipline for Hardware Interfaces" (de Muijnck-Hughes & Vanderbauwhede, ECOOP 2021):
HDLs realize both the *structure* and the *behavior* of hardware. Taking those as separate concerns
positions GAPL against its neighbours:

| | structure | behavior |
|---|---|---|
| Verilog / GAPL today | explicit | implicit (in the designer's head, and in hand-written FSMs) |
| HLS | inferred | explicit |
| Timeline types (Filament) | explicit | explicit, as annotations |
| **GAPL, aspirationally** | **explicit** | **explicit, as types** |

**The non-goal follows directly: do not become HLS.** The value of GAPL is that the designer decides
the structure. The type system's job is *not* to infer behavior or schedule on the user's behalf — it
is to make behavior that is currently implicit into something written down and checked. Test every
later decision against this.

### The problem: implicit behavior

Three motivating instances, and they are not all the same kind of problem:

- **The meaning of bits changes over time.** On an AXI-Stream, `wire[32]` is true for all time and
  therefore says almost nothing — the first beats are a header, the rest are a body. A *specification*
  problem.
- **Client/IP interaction spans many cycles.** Ordering and obligation between two endpoints. Also a
  *specification* problem, but about sequencing rather than about one endpoint's current contents.
- **Handshake boilerplate is replicated in every module.** An *abstraction* problem, not a
  specification one. An AXI-Stream Verilog module contains two interleaved programs — the datapath
  (what to compute) and the flow-control FSM (when it is legal to compute and emit). The second is
  almost entirely determined by the protocol, yet it is re-derived by hand in every module.

The third is why the first two are worth solving: **if the protocol is precise enough to check, it is
usually precise enough to generate the control logic from.** The specification work pays for the
abstraction work.

### The organizing spine: input/output semantics

Today a GAPL function denotes a netlist — a graph you poke cycle by cycle. It should denote a
*function from input values to output values*. The bridge is:

> **A protocol type is a (de)serialization scheme between an abstract value and a timed sequence of
> wire states.**

Take that seriously and much else follows rather than needing separate justification: testing (drive =
encode a value into a waveform, observe = decode), the "meaning changes over time" problem (a stream
*is* a serialization of a structured value across time), compatibility checking, adapter generation
between protocols carrying the same value, and the ability to state a correctness criterion at all.

It also gives a falsifiable acceptance test: **can the interpreter drive and observe a design knowing
only its interface types?** If yes, the semantics are right. If no, the types are under-specified.

### Goals

1. **Behavior explicit, structure still the user's.** Never infer structure or schedule. *(Non-goal: HLS.)*
2. **Every interface has a denotation** — a defined mapping between abstract values and timed wire behavior.
3. **Designs are testable from types alone.** The acceptance test for goal 2.
4. **Temporal compatibility is checked, not assumed.** Today only connection *shape* is checked.
5. **Flow control specified once, not per module.**
6. **Protocols are user-definable**, not a fixed builtin menu. (Standing item in `future-work.md`.)
7. **No silent hardware.** Whatever the compiler generates from a type should be predictable and its
   cost reportable. This is a *constraint on* goal 5, and the goal most in tension with the rest.

### Three levels of ambition

Independently deliverable; each has standalone value.

- **L1 — Describe & check.** Types document protocols; the compiler checks compatibility. No codegen
  change. Delivers goals 1, 4, 6.
- **L2 — Drive & observe.** Interpreter/testbench derived from types. Delivers goals 2, 3.
- **L3 — Synthesize.** Compiler generates flow control; user writes datapath only. Delivers goal 5,
  and is where goal 7 bites.

L2 is the highest-value near-term target — it validates the semantics before committing to generating
hardware from them, and L3's hard problems mostly evaporate if you have not yet promised to generate
anything.

---

## 2. The three layers

| layer | describes | status |
|---|---|---|
| 1 | spatial layout — which bits on which wires, for one beat | **exists** (`interface`) |
| 2 | temporal layout — how many beats make one value, what each means | **new** (`datatype`) |
| 3 | timing discipline — when a beat is accepted (`fire`) | **new** (annotation) |

This maps onto the three questions already posed in `protocols.md`: what data is in a request (the
abstract value), how it is represented (spatial + temporal encoding), and when each transmission is
made (flow control).

### The interface between layers 2 and 3 is one wire

Layer 3's entire job is to produce `fire` — "this cycle carries an informative event" — and to
guarantee layer-2 state cannot change when `fire` is low.

- **Layer 3 owes:** `fire`, and no state change without it.
- **Layer 2 owes:** given `fire`, visibility and next state.

Everything else in layer 3 (valid/ready, credits, static schedules) exists only to compute `fire`.
Consequences: layer 3 is *swappable* under a fixed layer 2 (`fire = 1`, `fire = valid`,
`fire = valid && ready` are three instantiations), and the old `raw`/`signal`/`stream` taxonomy in
`protocols.md` turns out to be three values of `fire` rather than three concepts.

**Stall-invariance is the enabling property, not a limitation.** "The layer-2 FSM cannot observe
stalls" is a property of the *description*, not a claim that stalls happen at runtime. When `fire` is
statically known, the FSM's state is a compile-time constant, the next-state logic folds away, and the
generated hardware is indistinguishable from a hand-written static pipeline. Had layer 2 been written
in terms of *cycles*, it would be fused to one schedule and could only ever lower one way. The
theoretical backing is Carloni/McMillan/Sangiovanni-Vincentelli's latency-insensitive design (TCAD
2001), which proves the informative-event sequence is invariant under arbitrary stalling.

**Where it is not free.** A layer-2 description is schedule-neutral only if its transitions are
**causal and local** — next state a function of the current beat and current state. Violations cost in
both regimes: *lookahead* (a length field in a trailer forces buffering the whole body), *late-arriving
control* (a transition depending on a value arriving the same beat puts data on the critical path to
`fire`), and *non-integer rate relationships* (forced bubbles). These are derivable, so the compiler
should **report** them — "this description requires 3 beats of lookahead ⇒ 3 registers of buffering" —
turning an invisible efficiency trap into a diagnostic. That is goal 7 applied.

Three regimes, worth keeping in mind because only the third pays the full latency-insensitivity tax:

| layer-2 extent | backpressure | hardware |
|---|---|---|
| static (`repeat(4)`) | none | nothing — the FSM evaporates at compile time |
| data-dependent (`until(last)`) | none | real FSM register, no handshake |
| data-dependent | yes | FSM + handshake + buffers |

---

## 3. Layer 1 — spatial (exists today)

Unchanged: `interface` definitions, records, aliases, vectors, flattened to `WireVector`s. See
`brainstorming/archive/interfaces.md` for the concept, but **not for the syntax** (see warning at top).

The one change is subtractive: `valid` and `last` leave the interface. Compare
`netfpga/src/md5/processor.gapl`'s `netfpga_packet_body`, which carries both and passes them through by
hand at lines 30–32. `last` becomes derived by layer 2, `valid`/`ready` by layer 3.

---

## 4. Layer 2 — temporal (new)

A **regular expression over beat types**:

```text
single(T)          // exactly one beat — the default when a bare interface appears
stream(T)          // one or more beats, bounded, carries a derived `last`
infinite(T)        // unbounded, no `last`
repeat(T, n)       // exactly n beats, n a static parameter
seq { a: D; b: E } // concatenation; phases may have DIFFERENT beat interfaces
```

### Decisions

**Bare interface means `single` in a lifecycle position.** So `i: word => o: word` already declares a
lifecycle, and every existing signature in `processor.gapl` keeps its exact text. The rule is
positional and needs stating because the same syntax means different things:

| position | bare interface means |
|---|---|
| function IO (`i: T => o: U`) | `single(T)` |
| function-typed parameter (`op: T => U`) | `single(T)` |
| record port (`data: wire[256];`) | layer-1 interface, *not* a lifecycle |

This collapsed a trade-off. Requiring explicit `single(T) => single(U)` on leaves would have been
uniform but rewritten every signature; keeping bare `T => U` as a layer-1-only function would have kept
two kinds of function whose difference is invisible. With bare-means-single they are the same option.

**`single(T)` means one beat, not one clock cycle.** Under backpressure the value is held until
accepted. Defining it in cycles re-entangles layers 2 and 3 and destroys the swappability property.

**Bounded vs. unbounded is explicit**, because configuration scope is otherwise *inexpressible*:

```text
needle: single(string), haystacks: infinite(stream(character))   // one needle for the whole run
needle: single(string), haystack:  stream(character)             // one needle per haystack
```

Under an implicit outer stream there is nowhere to attach the longer scope. Consequences:

- `fold` requires a **bounded** stream — an `infinite` one never reaches `last`, so a fold over it never
  emits. A type error, and a useful one.
- `scan` works over both. That gives CMS's persistent sketch a real type: a `scan` over an infinite
  stream, replacing today's raw `register(sketch4(16))` at `netfpga/src/cms/processor.gapl:29`.
- **`single` over an infinite lifetime lowers to a sticky latch** — it participates in the join until it
  first arrives, then latches and drops out, behaving as `@dataflow` from then on. Under `@ready_valid`
  the consumer simply stalls until configured, so there is no ordering obligation to check.
- **The invocation boundary is the outermost bounded lifecycle.** This answers `protocols.md`'s
  "who controls the FSM, i1 or i2?" A top-level module whose streams are all infinite is a standing
  process with no invocation boundary — the honest description of a hardware module. Where a bounded
  `stream` exists, its `last` is where per-lifecycle state resets. Two bounded `stream` inputs is
  genuinely ambiguous and should be a type error requiring an explicit declaration.

**`seq` phases may have different beat interfaces.** This subsumes the visibility idea from an earlier
draft's state-machine sketch (`brainstorming/archive/protocols.md:110–140`, which frames a protocol as
`hardware`/`states`/`transitions`): physically it is a **tagged union over a shared wire budget, where
the FSM state is the tag**. No in-band tag bits are needed because the state *is* the tag — which is the
same insight as "no valid signal is needed, the state carries that information."

**Nested streams cost one `last` per bounded level.** `stream(stream(character))` with the outer
infinite carries exactly one `last`, matching AXI-Stream. This is `brainstorming/archive/protocols.md`'s
`section_last`/`packet_last` pair (lines 153–179), re-derived.

### Two formulations of layer 2, and which is primary

- **Value-centric** (`protocols.md`): "a packet is a header followed by a body." Oriented toward
  serialization.
- **Access-centric** (the state-machine sketch in `brainstorming/archive/protocols.md:110–140`):
  "right now you may read these wires." Oriented toward checking module bodies.

**The value-centric description mechanically determines the access-centric one**; the reverse does not
hold — a visibility FSM never tells you what value is being carried. So serialization is primary and the
visibility FSM is *derived*. The `hardware`/`states`/`transitions` syntax from that draft is better
understood as something the compiler produces than something the user writes.

### The known gap: alternation

The lifecycle language has concatenation, bounded repetition, and Kleene star. **It has no `|`.** So it
can describe decoders (fixed structure, possibly variable length) but not parsers (structure chosen by
content). P4's parser model — `select(etherType)` transitioning to IPv4 vs. IPv6 — is not expressible.

This unifies with something already wanted: `brainstorming/archive/protocols.md:193` asks for "a format
where the length of the packet is computed based on a parsed value." Data-dependent *length* and
data-dependent *branching* are both instances of one feature:

> **Lifecycle structure may be bound at runtime from values extracted in earlier beats.**

It is additive, not disruptive — causality holds (selectors depend only on consumed beats), layer 3 is
untouched, the I/O invariant holds, and the physical mechanism is the tagged union already needed for
heterogeneous `seq`. What is missing is the type constructor.

**It also collides with the static parameter system**, which is where this whole design started.
Parameters are compile-time; this introduces a runtime value determining structure. The boundary:
*static parameters size the hardware; runtime-bound structure selects among already-instantiated
alternatives.* Every branch is built; the tag only selects. That is what makes it tractable.

**One decision it forces: may the choice graph contain loops?** VLAN stacking and IPv6 extension headers
are genuinely recursive. DAG-only gives bounded extent and statically computable maximum header length;
loops give unbounded, data-dependent extent and undecidable buffer sizing — the same cliff as
CSDF → boolean dataflow. **Recommend DAG-only, with `stream(T)` for the genuinely unbounded tail**,
which is the real structure of packet processing anyway.

Implementation is not novel: a lifecycle type is a regex over beats, and Brzozowski derivatives give a
textbook regex→DFA construction.

---

## 5. Layer 3 — timing discipline (new)

Two protocols only.

| | producer may stall | consumer may stall |
|---|---|---|
| `@dataflow` | no | no |
| `@ready_valid` | yes | yes |

These are cells of a 2×2 defined by **who may stall**. The fourth cell (consumer-only) is degenerate: a
producer that can never stall means data is always available, so the consumer gates its own state and
uses `@dataflow`.

### Why `@valid` was dropped

`@valid` (producer-only stalling) is the natural third cell and is what the NetFPGA pipeline actually
speaks. It is excluded from the surface language because **every one of its correctness properties is an
unchecked obligation**: init before stream, equal-latency forks, at most one stream input, nothing
downstream ever stalls. None is checkable without static latency analysis, and the failure mode is
silently dropped data. `@dataflow` and `@ready_valid` are each safe by construction.

It survives in the NetFPGA boundary wrapper, written once, where the "downstream never stalls"
assumption is stated in one place rather than implied everywhere.

**Do not re-expose it to recover performance.** The right move is an optimization pass: let the user
write `@ready_valid` and have the compiler downgrade where it can prove nothing downstream stalls — a
backward reachability question over the elastic graph (does any rate-reducing combinator, join, or
stalling sink lie downstream?). Far easier than the latency analysis `@valid` would need, and it inverts
the burden correctly: the compiler proves the unsafe thing safe rather than the user asserting it.

Same shape as the `register` decision: the dangerous construct lives in the compiler, not the surface
language.

### The annotation goes on the function; call sites coerce

```text
function f()@dataflow    i1: wire, i2: wire => o: wire { ... }

function g()@ready_valid i1: wire, i2: wire => o: wire {
    i1, i2 => f()@ready_valid => o;   // coercion: an island, with a join before it
}
```

- **Coercion is mandatory where protocols differ.** `i1, i2 => f() => o;` is a type error. **That error
  is the entire enforcement mechanism** — without it, islands get created invisibly and their cost
  becomes unreadable again.
- **Upgrade only.** `@dataflow => @ready_valid` is always sound. The reverse requires proving the
  function never stalls, so it is illegal.
- One definition, many protocols: `add` is written once as `@dataflow` and instantiated as
  `add()@dataflow` in a datapath or `add()@ready_valid` at a boundary. This is layer-3 swappability
  realized syntactically.

Mixed protocols across ports are fine and common: `@ready_valid` + `@dataflow`, e.g. a configuration
value alongside a stream. Per-port overrides are **deliberately not supported yet** — until a compelling
case appears, require both ports `@ready_valid`.

### Islands

A coercion site is an **island**: a region that freezes as a unit. The boundary is a function boundary,
which matters because the out-coercion needs uniform latency across all paths — and
`MinimalRegisterSolver`'s `VirtualIONode` constraint already gives exactly that per module.

- **In-coercion** (`@ready_valid → @dataflow`) is the **join**: `fire = (∧ valid_i) && ready_out`,
  `ready_i = fire`. Its arity is the coerced function's input port count — visible in the signature.
- **Out-coercion** (`@dataflow → @ready_valid`) is `valid_out = delay(fire, latency)`, plus
  `enable = fire` on every register in the island.

**Islands are rate-preserving by construction, and this enforces itself.** A `@dataflow` function cannot
contain a `fold` or `regroup`, because those are lifecycle-aware and need a handshake. So
`valid_out = delay(fire, latency)` is always correct without a separate check.

**Joins belong at merge points, not blindly at every function boundary.** A function that merely routes
whole channels to sub-functions needs no join — the merge happens inside the callee. Since `Flattener`
usually runs in `RECURSIVE` mode (module boundaries survive), this needs a per-function
**channel-merge summary** — which input port subsets are merged internally, which outputs fork from
which inputs — computed bottom-up, structurally like `HierarchicalMinimalRegisterSolver`'s timing
summaries.

### Lowering

- `enable` becomes a port on every generated module, threaded down the hierarchy, applied at
  **serialization**: every `always @(posedge clk)` becomes `if (enable)`. The netlist IR never models
  it, so `Retimer`, `CombinationalLoopDetector`, and the graph converters keep working unchanged.
- `valid`/`ready`/`last` **do** become real netlist wires, materialized **before** the transformer
  pipeline, so the `VirtualIONode` lag constraint keeps them aligned with the data they qualify.
- Inside a stall island, retime with **plain registers plus one global enable**, not elastic buffers. A
  full-throughput elastic buffer costs roughly 2× the flip-flops plus control, and buys nothing when
  everything freezes together. Elastic buffers are for cutting the **backward `ready` path**, which is
  combinational, has no registers, and is therefore invisible to retiming.

This is what `processor_controller` in `gapl_wrapper.v` does today, hand-written once at the top level.

### Fork and join

- **Lazy vs. eager is a fork distinction, not a join one.** A lazy fork asserts valid only when all
  consumers are ready (cheap, couples consumers, deadlocks on unequal-latency diamonds); an eager fork
  stores one copy and tracks *n* done-bits so consumers take independently.
- **Eager fork is a genuine primitive; "eager join" is not.** A fork sends the *same* data to *n*
  consumers, so sharing one copy plus *n* done-bits costs `width + n` bits where *n* FIFOs would cost
  `n × width`. A join carries *different* data per input, so tolerating skew means storing each one
  anyway — `join ∘ fifo-per-input` is already minimal. Decompose it.
- **Multi-output functions imply a fork**, and "partially delivered" is a real state — which is why an
  eager fork needs a register per output. The strategy choice needs somewhere to live in the syntax.
- **`join` is a lowering artifact; `zip` is a value-level combinator.** Conflating them is what creates
  an arity problem. Joins are generated (any arity, ports keep their own names and types); `zip`
  materializes the pairing as data.
- **`align` over `zip` when lengths may differ.** `align: stream(T), stream(U) => stream(pair(valid(T), valid(U)))`
  is total. This is *already invented* in `brainstorming/archive/protocols.md:290` as
  `function combine() ... => o: pair<valid<datatype>, valid<datatype>>`, and the stdlib already has
  `valid(T)`.
- **`zip` on unequal lengths: no static check, no hardware error handling — but implement it to
  deadlock, not desynchronize.** Requiring both `last`s to coincide costs the same as the alternative and
  gives a stalled pipeline you can see on a waveform, instead of permanent corruption of every
  subsequent lifecycle. Put the actual assertion in `simengine`, where it is free.

---

## 6. Combinators and state

**`register` is restricted to the stdlib**, not removed. `register` is exactly `scan` over an infinite
unit stream, so nothing is lost in principle — but the analogy to structured programming cuts finer than
it looks: Böhm–Jacopini needs `while`, not just `for`. Dropping `register` while offering only `fold`
and `map` would be dropping `goto` *and* `while`.

Four requirements before restricting it:

1. **`scan`, not just `fold`** — state visible during the lifecycle, not only at the end.
2. **A global/unbounded lifecycle** so cross-lifecycle state has a home (CMS's sketch).
3. **Memory as a distinct primitive** — random access is not a scan under any encoding.
4. **An explicitly marked escape hatch** for stdlib primitives and foreign IP; the elastic machinery
   cannot be built from itself.

Deferred but eventually needed: a time-multiplexing combinator with an explicit initiation interval,
which is where resource sharing gets recovered.

**Structuring *enables* the optimization you most need.** C-slow retiming — the fix for the fold-loop
problem below — is only safe if the compiler can prove successive iterations are independent. With state
expressed as `scan`/`fold` over an explicit lifecycle it can; with a raw `register` in a user-written
feedback path it cannot.

### The combinator set

```text
// rate-preserving, protocol-polymorphic
map(L: lifecycle, T: interface, U: interface, op: T => U)   i: L(T) => o: L(U)
scan(T, U, S: interface, step: last(T), S => U, S, init: null => S)   i: stream(T) => o: stream(U)

// rate-reducing, @ready_valid only (output is inherently intermittent)
fold(T, A: interface, step: T, A => A, init: null => A)     i: stream(T) => o: single(A)
regroup(in_bytes: integer, out_bytes: integer)              i: stream(beat(in)) => o: stream(beat(out))

// stateful transform with an optional trailing beat
scan_with_tail(T, U, S: interface,
               step: last(T), S => U, S,
               tail: S => valid(U),
               init: null => S)                             i: stream(T) => o: stream(U)
```

- **`scan` is the primitive.** `map` = scan with unit state; `fold` = scan + take-last.
- **`step` should be Mealy** (`T, S => U, S`), not `T, A => A`. The regex processor's state is an NFA-
  shaped struct but its output is one match bit (`netfpga/src/regex/processor.gapl:177`); CMS is the
  degenerate case where output *is* state.
- **A combinator's output protocol is determined by whether it changes the rate**, so there is no 3×N
  table. Rate-preserving preserves the protocol; rate-reducing forces at least `@valid`-like
  intermittency, hence `@ready_valid`.
- **Every function parameter is pure `@dataflow`.** Lifecycle position reaches them as *data*, via the
  stdlib's existing `last(T)` and `valid(U)` record interfaces, never as control. That is the whole
  ergonomic bet.
- **`map` requires a combinational `op`.** Non-zero latency enters only through hard macros (BRAM, DSP,
  vendor IP), which are latency-declaring channel functions, not lifted pure functions. This means v1
  needs no latency analysis — just "does `op` contain registers."
- **`map` is currently overloaded**: beat-wise (combinational `op`, generates logic) and per-lifecycle
  (`op` is a whole pipeline, generates *nothing* — a module already is a standing process). Deliberately
  deferred; needs distinct names eventually.

### Graduated access to layers 2 and 3

The tier a function occupies should be legible from its signature.

- **Tier 0** — pure datapath, `T => U`. Sees neither layer. Should be the overwhelming majority.
- **Tier 1** — combinator instantiation. Signature mentions lifecycles; body is combinators fed pure
  functions. Where essentially all pipeline-level business logic belongs.
- **Tier 2** — lifecycle-aware bodies. Read-only derived predicates (`i.first`, `i.last`, `i.phase`) and
  lifecycle-scoped state (`register_per_lifecycle`). Never raw wires.
- **Tier 3** — rate control as guarded actions: declare "consume a beat" / "emit a beat" and let the
  compiler derive valid/ready. This is Bluespec's guarded-method model, and it is precisely what
  `brainstorming/archive/examples/checksum_gadl.txt` asks for: *"I still need to think up some
  'syntactic sugar' for 'if valid and ready, do this'."*
- **Tier 4** — raw `valid`/`ready`/`fire`. For writing primitives (skid buffers, arbiters) and foreign IP
  wrappers. `axis_arbiter`, `duplicate`, `stream_case` in `brainstorming/archive/examples/checksum_gadl.txt`
  are all Tier 4 — and would become *stdlib*, not application code.

---

## 7. Rejected alternatives

Recorded so they are not re-derived.

| considered | rejected because |
|---|---|
| Implicit lifting of `f: T => U` to any lifecycle | Unsound for non-zero-latency `f`, and silently inserts control-delay registers. Replaced by explicit `map` with a combinational-only `op`. |
| Leaves declared `single(T) => single(U)` | Rewrites every signature; mislabels functions like `left_rotate` that are called from inside other pure functions. Dissolved by bare-means-single. |
| Higher-order wrapper for the `@dataflow → @ready_valid` coercion | Arity problem (one variant per shape), forces restructuring into a function purely to mark a boundary, and still does not expose the buffering knob. Replaced by call-site coercion. |
| Inferring islands by transitive `@dataflow` connectivity + LSP inlay hints for visibility | Developers do not read warnings; a cost with throughput consequences must be visible in the source. Replaced by mandatory call-site coercion. |
| `lazy_join` / `eager_join` as separate primitives | Lazy/eager is a *fork* distinction. "Eager join" is `join ∘ fifo-per-input`. |
| `@valid` as a surface protocol | Every safety property is an unchecked obligation; failure mode is silent data loss. |
| Padding produces `seq { message; footer }` | Wrong for MD5 — padding starts mid-beat, output blocks are homogeneous, only the *count* is data-dependent. |
| "GAPL's retiming lacks a constraint keeping `valid` aligned with data" | **False.** `MinimalRegisterSolver.kt:308-321` forces `r(sink) − r(source) = 0` on every edge touching a `VirtualIONode`, and `NetlistLeisersonCircuitConverter.kt:127-149` attaches one super-input and one super-output node. All IO shares a lag, so every input→output path shifts by the same delta. What survives is a `ready`-related concern: retimed registers have no enable, so in-flight beats need a stall enable or skid buffers. |
| Coroutine/generator-style FSM transformation | GAPL bodies are dataflow graphs, not statement sequences; suspension points need a program-counter model. Largest erosion of "the user decides the structure." |
| Adopting Calyx as GAPL's IR | Its value-add is the *control program* — scheduling, which GAPL declines to do. Would trade away `Retimer` integration for little. Revisit only if layer 3 goes dynamic with compiler-generated control. |
| Wire sorts now | Deferred, with a known bill (see open questions). |

---

## 8. Verified facts about the codebase

Each of these cost real investigation; do not re-derive or assume otherwise. Re-verified against the
tree during the 2026-09 merge of this document — locations below are current as of that pass.

- **`MinimalRegisterSolver.kt:308-321`** (`compiler/.../transformer/solver/`) constrains every edge
  touching a `VirtualIONode` to `r(sink) − r(source) = 0`; **`NetlistLeisersonCircuitConverter.kt:127-149`**
  (`analyzer/.../util/graph/`) attaches a single `superInputNode` to every source node and a
  `superOutputNode` from every output node. Consequence: all input ports share one lag and all output
  ports share another, so `valid`/`last` stay aligned with data through retiming automatically.
  **Caveat:** `SccSolver.kt:18-20` and `DagSolver.kt:22-23` (same package as `MinimalRegisterSolver`)
  both carry comments saying they do *not* enforce this invariant, citing `brainstorming/todo.md` —
  unverified whether that matters for where those solvers are actually used.
- **`gapl_wrapper.v`** (`netfpga/packet-processor/projects/reference_switch/hw/hdl/gapl_wrapper.v`)
  instantiates `packet_body_processor` with `.clock`, `.reset`, **`.enable`**, and
  `i$data/valid/keep/last` — **no `ready` on the kernel at all**. `processor_controller`
  (`.../hw/hdl/util/processor_controller.v`) drives that `enable` from an internal FSM gated on AXI
  valid/ready signals — not a direct 1:1 `tready`→`enable` wire, but functionally the same conversion.
  **The kernel is already a stall island.**
- **`axis_mutual_exclusion.v`** (`.../hw/hdl/util/axis/axis_mutual_exclusion.v`) is a three-state FSM
  (`STATE_INGRESSING → STATE_EGRESSING → STATE_RESETTING`) whose `module_reset` drives the kernel's
  reset (wired in `gapl_wrapper.v`). **The kernel handles one packet at a time with a full module
  reset between packets** — so there is no packet-level pipelining today, and the pipeline drains
  between packets.
- **`netfpga/src/md5/processor.gapl` is not streaming.** `packet_body_processor` is stateless per beat:
  `pad_nf_data` prepends padding hardcoded for a 256-bit message (`generate_256bit_padding` sets exactly
  bit 255 and bit 48 — the mandatory `1` and a length field of 256), `md5_single_round_hash`
  re-initializes from the IV constants every beat, and `keep`/`valid`/`last` pass straight through
  (lines 30–32). `md5_hash_block_from_iteration` statically recurses 64 deep, so the whole hash is
  combinational — **there is not one `register` in the file**; all pipelining comes from `--retime`.
  Three build variants exist under `netfpga/src/md5/` (`min-register-count/`,
  `per-port-min-register-count/`, `unretimed/`) with delay models, confirming the retimed/unretimed
  matrix.
- **`md5_hash_block(i: md5_input_block, input_state: md5_state) => output_state: md5_state` is already a
  fold step function**, complete with the Davies–Meyer feed-forward. MD5's block is **512 bits**
  (`md5_input_block = word[16]`, `word = wire[32]`); 128 bits is the digest width.
- **`SignatureGenericParameterType`** (`analyzer/.../builder/util/`) is a sealed interface with one
  `Integer` data object and two `// TODO`s (`Basic types`, `Functions`). `ParameterValue.fromNode` does
  no type checking beyond a null-check.
- **The stdlib already has `valid(T)`, `last(T)`, `conditional(T)`, and `pair(T, U)`** as record
  interfaces (`StandardLibrary.kt:34-58`) — hand-rolled versions of things this design needs.
- **`register` usage in application code** is: cross-lifecycle state (`netfpga/src/cms/processor.gapl:29`),
  per-lifecycle feedback (`netfpga/src/regex/processor.gapl:169`,
  `verilator-test/tests/email-with-opt/test.gapl:248`, `gapl-example/src/count-min.gapl:128`), and
  **hand-matched control delay** (`cms/processor.gapl:53-55` delays `valid`/`keep`/`last` by one to
  match the sketch's latency; `regex/processor.gapl:175` registers the whole output interface). The
  third category disappears entirely under this design.
- **`Flattener.Mode`** (`compiler/.../transformer/Flattener.kt:18-22`) is an enum with `NONE` / `ALL` /
  `RECURSIVE`; `RECURSIVE` is the common mode and inlines only *self*-recursive calls, leaving other
  module-invocation calls un-inlined — so there is no single flat netlist to analyze, matching the
  claim in substance.

---

## 9. Literature map

Confidence marked. Unverified items were recalled, not checked.

**Foundational**
- *Theory of Latency-Insensitive Design* — Carloni, McMillan, Sangiovanni-Vincentelli, TCAD 2001.
  Proves informative-event sequence invariance under stalling. **This justifies the layer-2/3 split.**
- *Synthesis of Synchronous Elastic Architectures* — Cortadella, Kishinevsky, Grundmann, DAC 2006 (SELF).
  Join/fork primitives, elastic buffers, eager vs. lazy forks.

**Types for hardware**
- *A Typing Discipline for Hardware Interfaces* — de Muijnck-Hughes & Vanderbauwhede, ECOOP 2021.
- *Modular Hardware Design with Timeline Types* (Filament) — Nigam, Thomas, Sampson, PLDI 2023.
- *Predictable Accelerator Design with Time-Sensitive Affine Types* (Dahlia) — Nigam et al., PLDI 2020.
- *Wire Sorts* — Christensen, Sherwood, Hardekopf, Balkind, PLDI 2021. Modular combinational-cycle safety.
- *Aetherling* — Durst et al., PLDI 2020. **Space-time types** — the same sequence laid out in space vs.
  time, with type-directed rewriting. Direct prior art for the gearbox/`regroup` problem.
- *ShakeFlow* — Han, Jang, Kang, ASPLOS 2023. Typed valid-ready channels + combinators. Already in
  `brainstorming/archive/examples/checksum_shakeflow.txt`.
- *Interface Automata* — de Alfaro & Henzinger, 2001. Component type as protocol state machine.

**Scheduling as a design space**
- *Combining Dynamic & Static Scheduling in High-Level Synthesis* — Cheng, Josipović, Constantinides,
  Ienne, Wickerson, FPGA 2020, and the DASS follow-on. **Most on-point for "support both efficiently."**
- Josipović et al., buffer placement and sizing for dataflow circuits, FPGA 2020. MILP for where elastic
  buffers actually need to go.
- *Dataflow Process Networks* — Lee & Parks, Proc. IEEE 1995. Canonical survey of KPN/SDF/CSDF/BDF and
  their decidability. Best map of the static/dynamic boundary.
- *A Framework for Comparing Models of Computation* — Lee & Sangiovanni-Vincentelli, TCAD 1998.
- *Dynamically Scheduled High-Level Synthesis* (Dynamatic) — Josipović, Ghosal, Ienne, FPGA 2018.
- *A Compiler Infrastructure for Accelerator Generators* (Calyx) — Nigam, Thomas, Li, Sampson, ASPLOS
  2021. Separates cells/wires from an explicit control program; `go`/`done`. **Unverified:** the static
  control extension (static `seq`/`par`, cycle-counted groups) is recalled but the paper not confirmed.

**Relevant but unverified**
- Session types in hardware (Scribble→hardware synthesis, and whether the ECOOP-2021 group has related
  work) — believed to exist, not confirmed.
- *Timed Multiparty Session Types* — Bocchi, Yang, Yoshida, CONCUR 2014. Adds clocks, but aimed at
  real-time deadlines, not cycle-level backpressure.

**Note on session types vs. timing.** Session types govern **order, not time** — sequencing, branching,
duality. They say nothing about cycles, rates, or backpressure. Mapping them onto layer 3's *timing* is
wrong; they belong to ordering and obligation. Also, typestate and session types are closely related and
inter-encodable, so splitting problems along that line is artificial — the axis that matters is
**order vs. time**. Duality does not transfer cleanly to hardware: both endpoints are wired
simultaneously and every cycle carries something, so duality appears as complementary obligations on the
same wires each cycle rather than as alternation.

---

## 10. Open questions

Ordered roughly by how much they block.

1. **Alternation in the lifecycle language** (§4). Unlocks a whole application class — parsers,
   classifiers with real keys, anything protocol-dependent. Now ranked above (2), since it also closes
   `brainstorming/archive/protocols.md`'s data-dependent-length request. Fastest partial path: **sum
   types + `match`**, which works under *today's* GAPL with `register` and `if_else`, and is a strict
   prefix of the full work. Implementation hint: represent a variant as a record with a tag plus one
   field per case; overlay onto shared wires later as an optimization. Reuses existing `WireVector`
   flattening.
2. **Rate relationships between two ports have no syntax.** Blocks two-stream joins, `zip`, and any
   `regroup` whose ratio is not statically known. A generated join silently asserts 1:1, and violation
   manifests as deadlock rather than a type error.
3. **`map` is overloaded** — beat-wise vs. per-lifecycle, one generating logic and one generating
   nothing. Deliberately deferred; needs distinct names.
4. **Direction reversal / control plane.** Runtime-configurable rule tables need a host write path
   (AXI4-Lite: address, write-enable, request/response). Information flow reverses by phase, which the
   unidirectional lifecycle language cannot express. This is the deferred session-types case. An
   `optional<T>` sketch was recalled from the original conversation as reaching for this, but **it could
   not be located anywhere in the current tree** (not in `brainstorming/archive/interfaces.md`,
   `brainstorming/archive/protocols.md`, or elsewhere) during the 2026-09 merge — flagged rather than
   silently dropped or pointed at the wrong file.
5. **Memory primitive.** Not expressible as `scan` under any encoding. Blocks Bloom filters, realistic
   sketch sizes, per-flow state. Read-modify-write hazards on consecutive same-bucket updates will need
   forwarding logic.
6. **`enable` is not `reset`.** After this change they sit on the same registers. A stalled cycle is not
   a lifecycle boundary; cross-wiring them is a subtle bug.
7. **Enable fanout.** A function-sized island puts its whole register set on one net. Fine at MD5's
   scale, a placement problem later.
8. **One stall domain per island.** A function with two outputs consumed at genuinely different rates
   cannot be one island. Worth a diagnostic rather than a silently under-performing design.
9. **`CombinationalLoopDetector` becomes the elastic-deadlock detector** once `ready` is a real netlist
   wire, since forks and joins can close a cycle through it. Correct behavior, but the diagnostic will
   point at compiler-generated nodes in a flattened graph. **This is the bill for deferring wire sorts** —
   budget a good error message.
10. **Time-multiplexing combinator** with an explicit initiation interval. Everything currently unrolls
    spatially; this is where resource sharing gets recovered.
11. **The `@ready_valid` → `@valid`/`@dataflow` downgrade pass** (§5). Without it, the first end-to-end
    comparison against hand-written Verilog will look bad for a non-fundamental reason.
12. **Per-port protocol overrides.** Deliberately deferred until a compelling case appears.
13. **Compile time.** Flagged during the 2026-09 merge as unconfirmed: the original claim was that
    `ModuleBuilder.kt` carries an open `TODO` noting an unexplained ~72s compile time on
    `verilator-test/tests/aes/test.gapl`. That TODO does not exist. What `ModuleBuilder.kt:102-114`
    actually documents is a *resolved* historical bottleneck — 696s down to ~0.05s, caused by
    `CombinationalLoopDetector`'s old flattening approach and already fixed. Whether layer-2/3 work
    reintroduces a compile-time concern worth tracking is genuinely open, but the specific evidence
    cited for it was wrong.

---

## 11. The efficiency question, and how to answer it

The standing worry: writing layer 2 without layer 3 in view produces less efficient code than Verilog's
co-design. The causality criterion in §2 bounds it, and the reporting discipline makes it visible — but
the magnitude is empirical.

**You are well positioned to measure it.** The repo has hand-written Verilog for these applications, a
NetFPGA synthesis flow, and `netfpga/src/md5/` carries a matrix of retimed/unretimed configurations with
delay models. LUT/FF/Fmax against the hand-written baseline is an available experiment.

Two findings already shape the expected result, in opposite directions:

- **Against:** a streaming `fold` creates a cycle through the compression function that Leiserson–Saxe
  cannot pipeline (register count around a cycle is invariant), flooring the clock period at the full
  64-round combinational delay. See `md5-lifecycle-sketch.md` finding 3.
- **For:** the current wrapper serializes packets with a full module reset between them, so there is *no
  packet-level pipelining at all today*. Internalizing lifecycle state removes that serialization —
  likely the largest single throughput win available, and a precondition for the C-slow fix to the
  finding above.
