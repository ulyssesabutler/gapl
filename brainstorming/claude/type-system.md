# GAPL Type System — Design Notes

**Status:** design in progress, nothing implemented. Written to carry the reasoning across sessions.

This is the "why" behind `md5-lifecycle-sketch.md` (the worked example) and `applications.md` (the
roadmap). Read this one first.

> **Layer-2 session (2026-09).** A working session took layer 2 on its own, with layer 3 deliberately
> set aside. It settled: the `interface`/`datatype` keyword split; **views** (§3); one base interface
> per datatype; cross-phase access forbidden in favour of `scan` state (§4); the spatial-vs-temporal
> alternation distinction (§4); the top-level type and the external-boundary rule (§4); nominal
> compatibility (§4); `map`/`oneach`, scan state scoping and seeding, and rate contracts (§6); and the
> line between what belongs in the language and what belongs in the stdlib (§6). Those sections carry
> the detail.
>
> **Layer 3 was un-parked at the end of that session**, on the grounds that six things had queued
> behind it: intermittent outputs from rate-reducing combinators, the v1 non-stalling restriction on
> combinator parameters, `map`'s combinational-`op` restriction, enforcement of the rate contracts,
> three of the nineteen surveyed FSMs (§8), and removing `processor_controller` from the wrapper. Note
> the *largest* single win — the roughly 5× from packet-level pipelining — is **not** among them: it
> comes from deleting `axis_mutual_exclusion`, which needs only per-lifecycle `scan` reset from layer 2.
>
> **Correction (2026-10):** "needs only layer 2" predates the rule that `@dataflow` means every port is
> `single` (§5). A function with a `stream` port must be `@valid` or `@ready_valid`, so layer 2's streams
> need layer 3 underneath them. That is why layer 3 is built first — see "Build order" in §1.

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
   **Deferred 2026-09** — the set is closed at `@dataflow` and `@ready_valid` for now; see §5.
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

### Build order (decided 2026-10)

The design was worked out with all three layers in view; the *build* is staged so each stage is a
bounded piece of work. (Stages are layers; the levels above are a separate axis, and "L1 first" applies
within each stage.)

1. **Views** — finishes layer 1 (§3). Views become types with projection, `fill` and `overlay`; no
   control wires. The stricter nominal
   connection check stays deferred (§10 item 18); views do not need it, and it is the only breaking
   change in this effort.
2. **Layer 3, `single` ports only** — protocol annotations, `valid`/`ready`, `fire`, call-site coercion
   (join and delay as stdlib functions; `enable` threading already exists), the no-join check, and the
   post-retiming delay pass (§5). Independent of views; they share only the grammar. Useful on its own
   for block-at-a-time kernels such as AES-CTR, though not yet for packet kernels.
3. **Layer 2** — datatypes, lifecycles, `last` and the branch tag, combinators (§4, §6). Must follow
   layer 3: `@dataflow` means every port is `single`, so a `stream` port needs `@valid` or
   `@ready_valid` underneath it. Nothing built in stage 2 should need redoing, since a `single` port
   is the one-beat case of a datatype. This is the stage that delivers the ~5× from deleting
   `axis_mutual_exclusion`, and lifts the six restrictions listed at the top of this document.
4. **FSM sugar** (`fsm-sugar.md`) — after layer 2, so that `match` is designed once and covers both
   value-level enums and `choice` tags rather than being retrofitted.

Stages 1 and 2 are independent and could run in either order; this is the chosen one.

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

### Views

A **view** is a named selection of an interface's bits — a set of names bound to bit ranges of a
**base** interface. **A view is a type** (decided 2026-10, reversing the September lens model; see
"Views are types" below): a view-typed node holds *only* the named bits, laid out like a record of its
fields, and converting a base value to it is a projection.

```text
interface axi_beat { data: wire[256]; keep: boolean[32]; }

view eth_hdr  of axi_beat { dst: data[0:47]; src: data[48:95]; ethertype: data[96:111]; }
view ipv4_hdr of axi_beat { version: data[0:3]; ihl: data[4:7]; /* ... */ }
```

**Views cost nothing.** `data[0:47]` is the same 48 wires under another name — the IR already models
this as `WireVector.Projection` rather than a copy, and in Verilog it is a slice of the same net. No
mux, no register, no LUT.

**Why they exist.** The original sum-type sketch proposed "a tag plus one field per case, no overlay,"
with overlaying onto a shared wire budget deferred as "a pure optimization for later" the compiler
would perform. That is in real tension with goal 7 — deferring the actual wire layout to an invisible
compiler pass is exactly what GAPL should not do. Views make the overlay something the user writes.
The "tagged union over a shared wire budget" in §4 stops being derived and becomes authored.

Their justification is **interop, legibility, and storage — not combinational area.** Cost hierarchy
for this design, worth internalizing before arguing about layout:

| tier | what | cost |
|---|---|---|
| 1 | wire naming, slicing, views, statically-known lifecycle extent | free — nothing emitted, or it constant-folds |
| 2 | a data-dependent layer-2 FSM | small, bounded — sized by *phase count*, not data width; order of tens of FFs |
| 3 | anything **stored** (buffers, skid buffers, a beat held across a stage) | real, proportional to width |
| 4 | the datapath (MD5's 64 rounds, AES's 10, the CMS sketch) | dominant |

Overlay-vs-duplicate is immaterial at tier 1 and decisive at tier 3: a parsed 5-tuple *held* while the
body streams past really does cost N× the flip-flops if each variant gets its own fields.

### Rules for views

- **A view is a type, and a view-typed node holds only the named bits.** Nodes and function ports may be
  view-typed. `h: eth_hdr` is 112 wires, not the base's 288. Projecting a base value to a view drops the
  unnamed bits, which is free — the same wires, nothing emitted.
- **Structural, not bit-flat.** A view is defined over the base's named fields and their slices, never
  over its flattened bit string — otherwise you can write a view cutting incoherently across `data` and
  `keep`, which is never meaningful.
- **Widening (view → base) is always explicit, and always says where the missing bits come from.** Two
  forms:
  - **fill** — the gaps take a stated value, e.g. `fill(eth_hdr, t, 0)`. `left_pad` in the stdlib is the
    existing idiom (`literal(padding, 0) => o[original:original + padding - 1]`).
  - **overlay** — the gaps take their values from a base value **the user supplies**:
    `base, h => overlay(t, eth_hdr) => o`. This is in-place modification (rewrite the TTL, pass the rest
    through), and it is expected to be the most common widening in the applications.

  There is **no implicit promotion**: the compiler never remembers what a view's unnamed bits were. See
  "Why overlay takes the base explicitly" below.
- **No don't-care fill in stage 1.** Under this model nothing needs one — every widening states its
  source — so stage 1 needs no X semantics in `simengine` or the IR.
- **External (hardware) ports may not be view-typed.** The port list has to match the bus exactly. A
  boundary keyword marks these (§4); stage 1 can use the top-level function's ports as a stand-in.

**Undriven wires are a hard error today, at bit granularity** — `NodeBuilder.validateWiresConnected`
(`analyzer/.../netlistir/builder/NodeBuilder.kt:121-147`) reports `UndrivenOutputPort` if *any single
wire* of an output group lacks a driver. Under the rules above that check stays exactly as it is: a
view-typed output has no unnamed bits to leave undriven, and every widening drives all of the base.

### Why overlay takes the base explicitly

The alternative is implicit promotion: "widen `h` back to `t`, filling each gap with the value it had
before projection." That requires the compiler to track provenance — which base each view value came
from — and it fails in every direction a value can travel:

- **Function boundaries.** `h` arrives at a function through a port; nothing inside knows the rest of the
  bits unless the compiler adds hidden ports to carry them.
- **Registers.** If `h` is held for a cycle, the rest of the base must be held too — storage the source
  never mentions, which goal 7 forbids.
- **Merges.** `if_else(c, h1, h2)` with `h1` and `h2` projected from different bases has no answer.
- **Constructed views.** An `eth_hdr` built field by field never had a base.

Explicit `overlay` makes all four disappear: a function that rewrites in place takes the base in its
signature; a held `h` means the user holds `base` too, as a visible register; merges and constructed
views raise no question. The only check is that `base` has the view's base interface type.

One requirement survives, and it is not new: `base` and `h` must belong to the same beat. If the path
modifying `h` is registered and the base path is not, they are misaligned — the same problem as any two
GAPL signals with different delays today, left to the user as it is now, and helped later by layer 3's
delay coercion.

### Views are types — reversing the September lens model

**Decided 2026-10.** In September a view was a **lens**: a base value stayed its base type and was read
or written *through* a view, and views produced no ports. "View as a narrower port type" was considered
then and rejected, for two reasons. Neither survives:

1. *"Narrow view ports solve a diagnostic problem, not a hardware problem."* The problem was partial
   writes leaving base bits undriven; since views cost nothing in hardware, restructuring the type system
   to fix a diagnostic seemed wrong. But that shows the narrow model is not *needed*, not that the lens
   is *better*. The hardware is essentially identical either way — synthesis deletes nets and flip-flops
   nobody reads. The difference is what the **source** says: under the lens, `register(t)` read through
   `eth_hdr` states 288 bits of storage and synthesis quietly removes 176; under view types the source
   states the 112 that get built. That is goal 7, and the storage argument views were justified by.
2. *In-place modification* (the L4 load balancer) seemed to need the lens. It does not help: GAPL allows
   one driver per wire, so `i => o; new_ttl => ipv4(o).ttl` drives the TTL bits twice, and the lens
   still leaves "everything except TTL" to be wired by hand. Both models need `overlay`.

What the lens did guarantee — that every view had a real base value under it, which is why the
top-level "raw interfaces only" rule mattered — held only because a view could never travel without its
base. Under view types it no longer holds, which is exactly why overlay must take the base explicitly
(above). The top-level rule survives for the simpler reason that external port lists must match the bus.

The one real ergonomic loss: building one base from several partial views ("write the Ethernet fields
and the IPv4 fields into one outgoing beat") read naturally as several writes through different lenses;
under view types it is a `fill` followed by a chain of `overlay`s.

### Conversions (decided 2026-10)

Three compiler primitives. They cannot be stdlib GAPL — a GAPL body cannot inspect a view's field list —
but their *use sites* need no grammar change: interface-parameterized primitives already parse as
ordinary calls (`if_else(wire[512])`, `register(T)`).

| primitive | shape | meaning |
|---|---|---|
| `project(t, v)` | `i: t => h: v` | keep the named bits, drop the rest |
| `overlay(t, v)` | `base: t, h: v => o: t` | write `h`'s fields into `base`, keep the rest |
| `zeros(T)` | `null => o: T` | a constant all-zero value of any interface |

- **There is no `fill`.** It is `overlay` onto a constant:
  `zeros(axi_beat), h => overlay(axi_beat, eth_hdr) => o`. `zeros(T)` is needed because `literal(size,
  value)` produces only a flat `wire[size]`, not a record — and it is the same primitive `fsm-sugar.md`
  §9 wanted for default values, so one primitive serves both tracks.
- **Projection is explicit.** No implicit `i => h`, per "explicit conversions at layer boundaries" (§4).
  The usual pattern is one `project` into a declared node, then field access (`h.dst`). An implicit rule
  could be added later without breaking code; removing one could not.
- **Parameter order is (base, view)** for both view primitives.
- **No fill declared on the view.** The September rules had partial views declare a fill; with `overlay`
  every widening states its source at the use site, so a declared fill has nothing left to do.

### Still open for stage 1

- **View-definition syntax** against `CST.g4` (the `view … of …` form above is illustrative).
- **Generic binding.** `T: interface` must accept a view, or views cannot pass through `register`,
  `if_else`, `vector_map`. Lowering each view to an ordinary record interface plus conversions makes
  this fall out.
- **Overlapping fields.** Fine for projection; on widening, two fields covering the same bit are two
  drivers. Forbid overlap or reject that widening.
- **Byte order.** On AXI, byte 0 is `tdata[7:0]`, so `ethertype: data[96:111]` reads byte-swapped
  relative to the protocol (`0x0800` as `0x0008`). Reordering wires is free, so a field could declare
  its byte order; otherwise every comparison with a protocol constant is a trap.
- **Non-adjacent fields** (one field joined from several slices) — free, but extra syntax.
- **Field types** (`dst: mac_addr` rather than `wire[48]`) — touches the deferred nominal checking.
- **Parameterized views** (`view hdr(n: integer) of beat(n)`), with slice bounds as static expressions.
- **Width-only checking** (§10 item 18, deferred) means `eth_hdr` connects to any 112-bit value; the
  base link is checked only inside the conversions.
- **Tooling** — LSP semantic tokens and go-to-definition for view fields; `simgen` wrappers for
  view-typed ports.

**Scope.** Views describe **static** header layouts, which covers the near-term applications. The
dynamic case — fields at runtime-computed offsets — is a stdlib `parse` over a stream, deliberately
deferred; see §6.

---

## 4. Layer 2 — temporal (new)

### The keyword, and what a datatype is anchored to

`brainstorming/examples/md5-fsm.md` already established the keyword split, with each layer defined over
the one below and all three sharing the shape of the existing `aliasInterfaceDefinition` in `CST.g4`
(`Interface Id parameterDefinitionList? expression`), so this is a small grammar delta:

```text
interface partial_byte_array(size: integer) { data: byte[size]; valid: boolean[size]; }   // layer 1
datatype  byte_array_stream(size: integer)  stream(partial_byte_array(size));             // layer 2
```

> **`md5-fsm.md` is stale on two points** (confirmed with its author): it types the NetFPGA top level as
> a bare `stream` rather than `infinite(stream(...))`, and it introduces `protocol` as a *definition
> form* for layer 3, which is superseded by the function annotation + call-site coercion in §5. The
> `interface`/`datatype` keywords from it stand. That file lives outside this directory and has not
> been edited.

**A datatype is anchored to exactly one base interface, and its constructs range over views of that
base.** Hard rule. The compact statement:

> The base interface pins the wire budget, the views are the alphabet, and the datatype is the regex
> over that alphabet.

This is what keeps the wire budget explicit: on a 256-bit AXI beat, every phase is physically one
`axi_beat`, and the datatype describes only how the meaning of those same 256 bits changes over time.
The apparent awkwardness of a narrow phase inside a wide base argues *for* the rule — anything crossing
a physical channel has uniform width anyway, so the cost is identical either way and the rule merely
makes you write it down. It also gives `regroup` a clean type: a function between two datatypes with
*different* bases, i.e. crossing base interfaces requires an explicit gearbox.

### Compatibility is nominal

**Decision (2026-09): two datatypes are compatible iff they are the same declared type. Aliases are
transparent; separate declarations with identical structure are distinct.** The same applies to views.

No new syntax is needed — GAPL already has the transparent half (`interface data_metabyte(size):
data_byte(size, 8)` is an alias, not a new type), and distinctness is simply the default. A `distinct`
or newtype keyword would only be required if *structural* compatibility were the default.

Note this is deliberately **inconsistent with layer 1 as it stands**, where the only connection-level
check in the entire analyzer is `BuilderDiagnosticKind.WireCountMismatch` — bit width. Two interfaces
with no relationship whatsoever connect silently if they flatten to the same wire count, so a 48-bit MAC
address happily drives a 48-bit checksum port. That looseness is an artifact of what was easy to
implement, not a design position, and it is precisely the failure "producer and consumer agree on what
the data is" exists to fix. Layer 2 is where the discipline arrives; it should not inherit layer 1's
permissiveness.

**Tightening layer 1 itself is deferred from this round** (settled 2026-10) — small to decide, large to
do, and the only part of this whole effort that would break existing code. Strict nominal checking would
reject every connection from a slice into a named field (`data[0:47] => o.mac`), which is pervasive. The
obvious repair — anonymous `wire[N]` structural, named interfaces nominal — partially reopens the hole,
since a field access like `checksum.value` yields an anonymous `wire[48]` that would then connect to
`mac_addr`; and view fields, being named slices, need a rule of their own. So it needs its own design.
Deferring blocks nothing here, because datatype compatibility is nominal *by datatype name* regardless of
how base interfaces are checked. When it is picked up, the migration path is **a warning first, promoted
to an error once existing code is migrated** — and keeping it out of this round means regressions in the
layer-2/3 work stay attributable.

The arguments for structural, recorded because they are real: a protocol type *is* a serialization
scheme (§1), so two identical schemes arguably are the same thing; external formats are defined by
standards bodies rather than by your naming, so two independent descriptions of Ethernet describe the
same Ethernet; and imported or generated definitions (from a Kaitai spec, a P4 program) would
interoperate without adapters. What decided against them:

- **Structural gives agreement about layout; nominal additionally gives agreement about meaning.** The
  layout errors are the rarer and less damaging class.
- **The forgotten-annotation asymmetry.** Under structural-by-default, failing to mark a type distinct
  yields an *unsafe* program (a raw packet silently satisfies a port expecting a validated one); under
  nominal-by-default, failing to alias yields a *verbose* one. This design has twice rejected defaults
  whose forgotten case fails silently — `@valid` in §5, inlay hints in §7.
- **Diagnostics.** "These names differ" is a tractable error message. "These two regex languages differ,
  at some position, for some reason" is not, and §10 item 9 already budgets one hard diagnostic for
  this compiler.
- **Cost.** Structural equivalence over lifecycle types means deciding language equality for
  regexes-with-predicates. Nominal needs none of it — see below.

The nominal choice does **not** restrict generic combinators: `map(L: lifecycle, T, U, op)` needs
parametric polymorphism and unification, not structural equivalence, and parametric polymorphism works
perfectly well in nominal systems.

**A distinct-type mechanism is still worth having eventually** — declaring a type that shares a layout
but not an identity lets you encode properties the bits don't carry ("this frame's checksum has been
verified"), so that the only way to obtain one is to call the function that establishes the property.
Under nominal-by-default that falls out for free rather than needing a keyword.

A **regular expression over beat types**:

```text
null               // zero beats
single(T)          // exactly one beat — the default when a bare interface appears
stream(T)          // one or more beats, bounded, carries a derived `last`
infinite(T)        // unbounded, no `last`
repeat(T, n)       // exactly n beats, n a static parameter
seq { a: D; b: E } // concatenation; phases may have DIFFERENT beat interfaces
choice { a: D; b: E } // alternation — NO embedded selector; the producer chooses
```

> **`choice` carries no selector** (decided 2026-09). A datatype never contains an expression. The
> *producing* function decides which branch its output takes, and publishes that decision as a tag on
> the channel; consumers are bound by it. See "Alternation: the producer chooses" at the end of this
> section — and note that the `choice` material immediately below, which assumed an *embedded* selector
> (`choice(h0.eth.ethertype) { ... }`), is superseded by it.
>
> **How any of this is realised in hardware** — what the compiler actually builds from a datatype, and
> which control wires are transmitted rather than derived — is in §5's **"Realization"** subsection.

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

**Two kinds of alternation, and only one of them is `choice`.** Keeping these apart matters because
their costs differ by orders of magnitude:

| | selector and selected content | mechanism | cost |
|---|---|---|---|
| **spatial** | in the *same* beat (ethertype at bytes 12–13 decides how bytes 14+ of beat 0 read) | comparator + mux, everything on the wires at once | combinational, cheap |
| **temporal** (`choice`) | continuation differs in *beat count* after this phase | real FSM state | small but real |

Spatial alternation is the **sum type** — the thing this design started from, before views arrived —
and it needs to come back in, because the straddling problem forces it: protocol headers do not align
to beat boundaries, so a beat routinely contains a selector and the field it selects. It is the cheap
one. `choice` is the expensive one, and it is what the rest of this section is about.

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

**On the automata theory** — an earlier draft of this document claimed "implementation is not novel: a
lifecycle type is a regex over beats, and Brzozowski derivatives give a textbook regex→DFA
construction." That is too glib, and it points at the wrong problem:

- Classical Brzozowski assumes a finite alphabet of concrete symbols. Ours is 2²⁵⁶ possible beats with
  transitions guarded by *predicates*, which is **symbolic finite automata** — transitions labelled by
  predicates from a decidable Boolean algebra, where the derivative construction does generalize
  (Veanes et al., §9). Established, but a meaningfully larger body of work than "textbook." Predicates
  that also read `scan` state push further still, toward extended/register automata.
- **More importantly, none of it is needed for codegen.** Deriving the FSM from a datatype is plain
  structural recursion: a `seq` is a counter, a `choice` is a branch, a `stream` runs until `last`. The
  automata machinery is only needed to decide **structural type equivalence** — whether a producer's
  datatype and a consumer's describe the same beat language — and only if datatype compatibility is
  structural rather than nominal.

**It is nominal, so none of this is needed** (see "Compatibility is nominal" above). Codegen is
structural recursion over the datatype; compatibility is name equality. The symbolic-automata work
drops off the list entirely. It would return only if structural compatibility, or subtyping over
lifecycle types, were ever wanted.

### Decisions for `choice` and spatial alternation

Settled in the 2026-09 session.

**The `select` lives in the datatype definition, not in the function body** (confirmed 2026-09). The
header parser sketch writes it that way —
`h1: single(select(h0.eth.ethertype) { 0x0800 => beat1_v4; ... })` — and the alternative of matching
inside a function body was raised only because the datatype form appears to read an earlier phase's
field. That objection is resolved above: the compiler registers the *resolved branch index*, one bit
for a two-way select rather than the selector's full width. Putting it in the datatype is also what
makes the guarantee available to consumers, which is the point of the next decision.

**Spatial alternation is a language construct, not a stdlib function.** It emits only a mux over wires
already present, and `if_else` could nearly express it today — but what you want from it is the
*guarantee* that you cannot read `ipv4.src` off something that resolved to IPv6, and a stdlib function
cannot provide a compile-time property. This sharpens the language/stdlib line from §6:

> Anything whose value is a **circuit** may live in the stdlib. Anything whose value is a **static
> guarantee** has to be in the language.

**Selector provenance falls out of the access rule.** Since a phase's view is live only during that
phase, `choice(hdr.ethertype)` is legal only while still in `hdr` — which is exactly the moment the
transition out of it is computed. So:

| selector reads | legality | cost |
|---|---|---|
| the phase being transitioned out of | legal | a comparator on the path to the FSM transition — §2's "late-arriving control" |
| anything earlier | not reachable as a view; must be in `scan` state | registered, free |

The two are syntactically distinct, so the cost is visible in the source without needing a report.

**Every `choice` declares an `else`, and don't-care is the sugared form.** Requiring it turns an
implicit assumption into a written assertion; sugaring it keeps the efficient option available. Two
constraints:

- **Don't-care means "synthesis may route this to any existing branch," not "undefined state."** For a
  temporal `choice` the FSM lands somewhere physically, and an undefined state may never reach `last` —
  which desynchronizes every subsequent lifecycle on that channel, permanently. Same reasoning §5 used
  for `zip`: deadlock is debuggable, desync is not. Routing to an arbitrary existing branch costs
  nothing and keeps the lifecycle terminating.
- **`simengine` asserts on the don't-care case.** This is the lesson of Verilog's
  `full_case`/`parallel_case`, whose bad reputation came not from the idea but from simulation and
  synthesis disagreeing about unlisted cases. Synthesize to nothing, assert in simulation — the same
  move §5 makes for `zip`, "where it is free."

Spatial alternation may leave its no-match case as genuine don't-care: undefined bits, bounded damage.

**A selector is an `expression`, not a `circuitExpression`.** `CST.g4:68-82` already gives arithmetic,
comparison, logical operators and accessors — enough for `ethertype == 0x0800` or
`version == 4 && ihl > 5` — with zero grammar delta and combinational-by-construction for free. Letting
a full circuit expression into a datatype definition would embed the connection sublanguage inside type
definitions. Anything more complex is computed into `scan` state by an ordinary function first and
branched on there, which is already the idiom for cross-phase data.

Selectors must additionally be **register-free**. Note this has an independent justification from
`map`'s superficially similar rule: a selector sits in the FSM's state → next-state → state loop, so a
registered selector would mean branching on state from a *previous cycle* — different semantics, not
merely different timing. For the same reason, **selector logic is not retimeable** (the loop-invariant
argument from `md5-lifecycle-sketch.md` finding 3), so deep selector logic lowers the clock rather than
being pipelined away. Allowed, but worth keeping shallow.

**Branches need not reconverge.** `seq { pre; choice { ... } }` is legal and is the common packet-
processing shape; each branch then independently reaches `last`. Reconvergent branches are equally
fine — the post-choice phase has one type on all paths, since it is one `seq` phase.

**Nesting is allowed.** Ethernet → IPv4 → TCP/UDP is two levels. DAG-only bans cycles, not depth.

### `null`, and what happens when `last` arrives early

Both settled after the header-parser sketch, which found them together (see
`header-parser-sketch.md` findings 3 and 4). They are two halves of one question: **what does it mean
for a lifecycle to end before the `seq` structure is exhausted?**

**`null` is a zero-beat lifecycle.** It exists because `stream(T)` is one-or-more, and a body of zero
beats is not an edge case — a minimum-size Ethernet frame is 64 bytes, exactly two beats, and the
headers account for 54 of them, so the body is empty. `null` composes two ways: as a `choice` branch
(`choice(...) { ... => stream(T); ... => null; }`) and as the way to declare a trailing phase absent.

> *Naming:* GAPL already uses `null` in function IO for "no ports"
> (`function md5_initial_state() null => o: md5_state`). Reusing the keyword for "no beats" is the same
> intuition in a different position — deliberate, not an accidental collision.

The alternative considered was a stream whose length is declared before its first beat, which would
also admit zero. That is the **length-derived `last`** already filed as §10 item 15 (Kaitai's substream:
a down-counter rather than a flag). It does not help here — a packet body's extent comes from `tlast`,
not from a declared count — so `null` is the answer for this case and the counted form stays deferred.

**`last` returns the FSM to its initial state, regardless of phase.** A frame shorter than the datatype
permits — a runt, from a collision or a hostile sender — ends during `h0` when the type said at least
two beats would arrive. Without a rule, the FSM can still be mid-`seq` when the next packet starts, so
it reads that packet's first beat as its own second, and every subsequent packet is misparsed:
permanent desynchronization, the same catastrophic class as an undefined `choice` branch.

Worth separating two responses, because only one is obligatory:

- **Error detection** — "this packet was malformed." Optional and application-specific; drop, count, or
  ignore as the design prefers.
- **Resynchronization** — "the FSM is ready for the next packet." **Not optional**, and not really error
  handling. It costs nothing (reset on `last`) and is required whether or not anyone cares about the
  malformed packet.

Today's NetFPGA setup is accidentally immune, since `axis_mutual_exclusion` full-resets the kernel
between packets — but removing that serialization is the largest throughput win available
(`md5-lifecycle-sketch.md` finding 5), and this rule is a precondition for removing it.

**What a consumer *emits* on a truncated lifecycle is deferred** (2026-10; §10 item 19). Resync is
settled; the open half is the output. A header parser that saw `h0` but never `h1` can (1) emit anyway
with the unseen fields don't-care, (2) drop its output, or (3) flag it by typing its output as
`choice { ok: single(header); runt: null }` and picking `runt` on an early `last` — producer-chooses, no
new mechanism. The working lean: (1) by default, (3) as opt-in, and **never (2)**. Dropping breaks the
one-output-per-lifecycle rate that split/recombine depends on: the header path loses the packet while
the payload path does not, and the rejoin pairs every later header with the wrong payload — the same
desynchronization one level up. Dropping, if wanted, belongs after the rejoin, as a filter on `runt`.
Option (3) needs the body to observe "`last` arrived early", which is part of the grammar's
phase-surfacing question.

### Alternation: the producer chooses

**Decided 2026-09, superseding the embedded-selector `choice` material above.** `choice` survives as a
constructor but carries **no selector**:

```text
datatype ip_payload choice { v4: stream(ipv4_beat); v6: stream(ipv6_beat) }
```

The *producing* function decides which branch its output takes, using ordinary value-level logic in its
own body, and publishes that decision as a **tag on the channel** — one more control wire alongside
`valid`, `last`, `ready` and `phase`, costing log₂(branches) bits. Consumers are then bound by it: they
must match on the tag before reading branch-specific fields, and exhaustiveness is checkable.

The virtue is that it puts three different things in three appropriate places:

| what | where |
|---|---|
| selector *logic* | in the producing function, as ordinary value-level code |
| choice *structure* | in the datatype, with no expression in it |
| choice *outcome* | on the channel, as a tag |

**This eliminates two clusters of questions that an embedded selector forced**, because the producer
always picks a branch the type declares:

- **The mandatory `else` and its don't-care semantics.** Those existed because a selector *expression*
  might match nothing. There is no unmatched case here.
- **Selector provenance** — the whole four-cell analysis of same-beat versus earlier-beat selectors, and
  the "late-arriving control puts data on the critical path to `fire`" cost. With no expression there is
  no provenance question; the producer decides however it likes internally, and by the time the tag
  reaches the channel it is a registered value.

**What it costs:** the phase FSM is not purely a counter. It gains a registered branch tag selecting
which per-branch transition table applies — so it is sized by phase count × branch count rather than
phase count alone. Still tens of bits, still no deep logic.

**DAG-only still applies**, for the same reason as before: loops in the choice graph give unbounded,
data-dependent extent and undecidable buffer sizing.

**And it resolves the `null` question.** An optional trailing phase is just
`choice { present: stream(T); absent: null }`, with the producer picking — which it can, since it knows
whether `last` already arrived. No separate mechanism needed.

*History:* an intermediate position — no `choice` at all, alternation purely as value-level sum types
and `match` inside parser functions — was recorded and then superseded by this one. It would have given
up the type system's knowledge that beat structure varied ("type-safe parsed output, untyped
lifecycle," `applications.md` option A). The producer-chooses model keeps that knowledge while still
keeping expressions out of datatype definitions, which was the original motivation.

### The access surface: a phase's view is live only during that phase

**Cross-phase field access is forbidden. Anything you need later is `scan` state.** The L4 load
balancer does not reach back for `i.eth.dst` while the body streams; it runs a `scan` whose accumulator
is a `five_tuple`, filled during the header phases and read thereafter.

Two reasons, and the second is the real one:

1. The storage appears **in the signature**, as the declared state type. A `scan` over `five_tuple`
   says "104 flip-flops" in the source rather than in a compiler report. Reporting an implied cost was
   considered and rejected — a cost that matters has to be visible where the code is written, which is
   the same reasoning §7 used to reject LSP inlay hints for island boundaries.
2. §2 says a layer-2 description is schedule-neutral only if its transitions are **causal and local** —
   next state a function of the current beat and current state. That is exactly what `scan`'s `step`
   receives. So forbidding cross-phase access is not an arbitrary restriction; **it is §2's causality
   criterion made syntactic.** Cross-phase field access would permit descriptions that violate it,
   which is precisely why it would then need cost-reporting to stay honest.

A useful consequence for the alternation work: since a selector can only reference what is already in
scan state, and scan state can only contain what was extracted from consumed beats, **causality holds
by construction** rather than needing scoping rules to enforce it.

### Position is unambiguous, because a channel can delimit only one variable extent

A `packet = seq { h1: single(V1); h2: single(V2); hb: single(V3); body: stream(V4) }` carries exactly
one `last` — the body's — and because `body` is the final phase, the body's `last` *is* the packet's
end. The three `single` phases are statically sized, so tracking them is a 2-bit counter: §2's
"static extent → the FSM evaporates" row.

This generalizes: **at most one variable-extent level is delimitable per channel**, because the bus
carries one `tlast`. A datatype with two variable-extent phases (`seq { a: stream(X); b: stream(Y) }`)
is not physically expressible on a single AXI-Stream at all — there is no delimiter to mark where `a`
ends. So the rule falls out of the boundary rule below rather than needing to be imposed.

### The top-level type, and what "external" constrains

**A NetFPGA pipeline stage's top level is `infinite(stream(axi_beat))`**, not a bare `stream` and
certainly not raw. The deciding argument was configuration scope, per the bounded-vs-unbounded decision
above: `ss() needle: single(string), haystack: stream(char)` and
`ss() needle: single(string), haystacks: infinite(stream(char))` describe genuinely different
applications — one needle per haystack versus one needle for the life of the design — and only the
explicit form can say which.

An earlier instinct that "top-level interfaces must be raw" was mis-scoped. It is not the lifecycle that
must be raw; it is the **base interface at an external boundary**:

> At an external boundary, the base interface must be exactly the physical wire budget, and the
> lifecycle's **delimiters** must map onto signals the external protocol already carries.

For AXI-Stream: base = `tdata`/`tkeep`, `stream`'s `last` ↔ `tlast`, and (when layer 3 returns)
`@ready_valid` ↔ `tvalid`/`tready`. Every part of the layer-2 description corresponds to a wire that
already exists, so the type is **pure description** — zero synthesized hardware at the boundary, which
is the L1 "describe & check" ambition landing where it should. Structure *above* the delimiters
(`seq`, `choice`, views) is free there, because it is reconstructed internally by counting beats —
"the state is the tag, no in-band bits needed." What you cannot declare is a lifecycle whose delimiters
the bus does not provide.

A **keyword marks these boundaries**. It is needed anyway: the contract already exists implicitly, since
`gapl_wrapper.v` instantiates `packet_body_processor` by name with a hand-written port list. The same
marker should serve the foreign-IP escape hatch §6 requires — a port connecting to something GAPL did
not generate — rather than inventing a second mechanism, and it also gives `Flattener` and the Verilog
emitter a single thing to key off for "this module's port list is load-bearing."

### A general principle, now decided three times

**GAPL requires explicit conversions at layer boundaries.** No implicit widening (view → base), no
implicit lifting between lifecycle levels (§6), and mandatory call-site coercion once layer 3 returns
(§5). Bare-means-`single` is not an exception: it is notation for how a written type is *read*, not a
silent conversion of a value at a connection.

---

## 5. Layer 3 — timing discipline (new)

Two protocols only.

| | producer may stall | consumer may stall | may join |
|---|---|---|---|
| `@dataflow` | no | no | n/a — no extent |
| `@valid` | yes | no | **no** |
| `@ready_valid` | yes | yes | yes |

Cells of a 2×2 defined by **who may stall**. The remaining cell (consumer-only) is degenerate: a
producer that can never stall means data is always available, so the consumer gates its own state and
uses `@dataflow`.

**Three protocols, and they correspond exactly to §2's three regimes** — which is the strongest
argument that this is the right number:

| §2 regime | protocol |
|---|---|
| static extent, no backpressure — "the FSM evaporates at compile time" | `@dataflow` |
| data-dependent extent, no backpressure — "real FSM register, no handshake" | `@valid` |
| data-dependent extent, backpressure — "FSM + handshake + buffers" | `@ready_valid` |

With only two protocols, one regime had no name.

### `@valid` — dropped, then readmitted with a rule

**Readmitted 2026-09, under one restriction: a `@valid` function may fork but may not join.** To fork
*and* join you create an island, exactly as with `@dataflow`. The original rejection (preserved below,
because its analysis is what the restriction is answering) held that "every one of its correctness
properties is an unchecked obligation." The restriction changes the status of three of the four:

| obligation from the original rejection | under fork-but-no-join |
|---|---|
| equal-latency forks | **evaporates** — branches need alignment only because they eventually meet; forbid joining and latency skew stops mattering |
| at most one stream input | **becomes a rule**, not an obligation |
| nothing downstream ever stalls | **becomes checkable** — mandatory call-site coercion already catches wiring a `@valid` producer to a stalling consumer |
| init before stream | **survives** — a `@valid` consumer cannot wait for a configuration value |

The one residual turns out to be **already forbidden by the no-join rule** (settled 2026-10). A
configuration port is a second input channel, and firing a function with two input channels requires
both valid — `fire = valid_a && valid_b` — which *is* a join. So `@valid` functions cannot take
configuration ports at all. (An earlier draft offered "require the config port to be `@ready_valid`" as
an alternative; that is incoherent, since a function carries exactly one protocol and per-port overrides
are rejected.) The escapes:

- **compile-time configuration** → a static parameter, which has no arrival-timing problem;
- **runtime configuration** → make the whole function `@ready_valid`, where §4's sticky latch lets it
  stall until the value arrives.

Note the difference in kind from what was rejected — the original argument was *expose it and trust
the user*; this is *expose it with the dangerous compositions forbidden*.

**Where the no-join check lives** (settled 2026-10): alongside coercion checking. Both are per-call-site
protocol checks, and both need only protocol-per-function and datatype-per-channel, which are known after
resolution — so it can run early with a diagnostic pointing at the offending call. The rule: *inside a
`@valid` function's body, no call may take more than one input channel.* Fork-and-join is not banned
outright; it goes **inside a single-input `@dataflow` island**, where there is no handshake at all, so
forking and recombining are ordinary wires and gates rather than a join in the handshake sense. The
island's boundary has one input and no join.

**Two consequences.** `@valid` is what the NetFPGA pipeline actually speaks, so this is the protocol
that lets `processor_controller` disappear without forcing full `@ready_valid` into the kernel. And it
may substitute for §10 item 11, the downgrade pass, which exists specifically to recover `@valid`-like
efficiency from `@ready_valid` automatically — less urgent once you can simply write it.

### The original rejection, preserved

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

**Per-port protocol overrides are rejected, not merely deferred** (clarified 2026-09; an earlier draft
of this section said both that "mixed protocols across ports are fine and common" and that overrides
were "not supported yet," which contradicted itself).

The motivating case was a configuration value alongside a stream — seemingly a port that wants
`@dataflow` while its neighbour wants `@ready_valid`. Supporting that introduced problems of its own,
and the case turns out to be served by **lifecycle choice instead of protocol choice**: a configuration
value that persists for the life of the application is a `single` over an `infinite` lifetime, which §4
already lowers to a sticky latch — it participates in the join until it first arrives, then latches and
drops out. So a function carries one protocol, and the situation that appeared to need two is expressed
in layer 2.

### Assigning protocols (2026-09)

**Every function carries a protocol.** There is no unannotated state.

**Every existing GAPL function is already `@dataflow`**, and this is not a coincidence to be checked
case by case — existing code uses bare interfaces throughout, bare interfaces are implicitly `single`
(§4), and `@dataflow` requires exactly that every port be `single` (§6). So **layer 3 is purely
additive: no existing program changes meaning.** That is worth protecting as the design proceeds.

**`oneach` is transparent.** It emits no hardware of its own, and it inherits the protocol of the
function it wraps — which has temporal extent and is therefore `@ready_valid`.

**The protocol set is closed: `@dataflow` and `@ready_valid`, hardcoded.** This **defers goal 6** from
§1 ("protocols are user-definable, not a fixed builtin menu"), which is also a standing item in
`brainstorming/future-work.md` as "how to allow the user to define arbitrary handshakes." Recorded as a
deliberate deferral rather than left to silently contradict the goal list.

When it is picked up, §2 supplies the framing and makes the problem much smaller than it sounds:
"everything else in layer 3 — valid/ready, credits, static schedules — exists only to compute `fire`."
A user-defined protocol is therefore a user-defined way to compute `fire`, not an open-ended handshake
description language.

### Islands

A coercion site is an **island**: a region that freezes as a unit. The boundary is a function boundary,
which matters because the out-coercion needs uniform latency across all paths — and
`MinimalRegisterSolver`'s `VirtualIONode` constraint already gives exactly that per module.

**So creating an island means writing a `@dataflow` function and promoting it inside a `@ready_valid`
one** (confirmed 2026-09). There is no separate island-declaration mechanism; function boundaries are
the only boundaries.

**Do not conflate this with the external-boundary marker** from §4. That one marks a port connecting to
something GAPL did not generate — it bars views, pins the base interface to the physical wire budget,
and doubles as the foreign-IP escape hatch §6 requires. An island marks a *protocol transition*, which
is an internal concern. The two coincide at the top-level module, which is both, but they answer
different questions and merging them would be confusing later.

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

  > **Two corrections (2026-09), both verified against the code — see §8.** First, this is **already
  > implemented**: the serializer emits `if (reset) r <= 0; else if (enable) r <= next; else r <= r;` for
  > every register, and both signals are already threaded, which is how `gapl_wrapper.v` passes
  > `.enable` into the kernel today. Second, "keep working unchanged" holds **only while all registers
  > are interchangeable.** Retiming does not move registers — it deletes them, collapses them into
  > integer edge weights, and re-synthesizes anonymous shared shift registers whose bits aggregate
  > across unrelated sources. Per-lifecycle reset would make accumulators distinguishable from pipeline
  > registers, and nothing survives retiming to carry that distinction. See §10 item 6.
- `valid`/`ready`/`last` **do** become real netlist wires, materialized **before** the transformer
  pipeline, so the `VirtualIONode` lag constraint keeps them aligned with the data they qualify.

  > **The stated reason is retired (2026-09); the conclusion is weaker than it looks.** Retiming happens
  > *inside* islands, islands are `@dataflow` and therefore have no `valid`/`last` at all, and the
  > out-coercion **synthesises** `valid_out = delay(fire, latency)` from the island's known latency. So
  > nothing travels through retimed logic needing alignment, and "materialize early to stay aligned" is
  > not a requirement. The hand-written precedent is `cms/processor.gapl:53-55`, which delays
  > `valid`/`keep`/`last` by one to match its latency — delay-at-boundary, not carry-through. What
  > replaces this is in "Realization" below.
- Inside a stall island, retime with **plain registers plus one island-wide enable**, not elastic
  buffers. A full-throughput elastic buffer costs roughly 2× the flip-flops plus control, and buys
  nothing when everything freezes together. Elastic buffers are for cutting the **backward `ready`
  path**, which is combinational, has no registers, and is therefore invisible to retiming.

  > *Wording clarified 2026-09: "island-wide," not "global."* There are two different enables and
  > conflating them causes confusion. The **wrapper's** enable (`processor_controller`) is one signal
  > for the entire kernel, hand-written, and is the thing this design deletes — it is why
  > `axis_mutual_exclusion` has to serialize packets around the kernel. The **island** enable is
  > compiler-generated per `@dataflow` function. Same mechanism, radically different granularity; the
  > second is what replaces the first.

- **Elastic buffers as the *general* retiming primitive: considered and deferred (2026-09).** The
  proposal was for retiming to insert FIFOs rather than plain registers everywhere, so backpressure
  propagates correctly with no enable at all. Deferred for three reasons:
  - **The elastic graph is coarse — islands are its nodes.** All the deep combinational paths (MD5's 64
    rounds, AES's 10, the hash functions) live *inside* islands, where plain registers are correct. What
    remains between them is shallow: a phase counter, a comparator, a join computing
    `fire = (∧ valid_i) && ready_out`. Few edges, so few buffers. Contrast fine-grained elastic HLS,
    where every operation is a node — which is why buffer placement is MILP-hard there (Josipović et
    al., §9) and a much smaller problem here.
  - **It would not fix the timing problem we actually have.** `md5-lifecycle-sketch.md` finding 3 is the
    `fold` recurrence, where register count around a cycle is invariant so nothing can pipeline it.
    Elastic buffers do not change that invariant. C-slow retiming is the fix there, not buffering.
  - **Deferring costs nothing to reverse**, because the decision that *would* be expensive to retrofit —
    `ready` being modelled in the netlist IR — is being taken anyway, for independent reasons.

  **Revisit trigger**, stated narrowly on purpose: *a path in the elastic layer between islands that
  fails timing and cannot be fixed by restructuring the islands.* A failing path inside an island is
  ordinary retiming's job; the backward `ready` path is already handled by boundary buffers above; a
  recurrence is C-slow's.

This is what `processor_controller` in `gapl_wrapper.v` does today, hand-written once at the top level.

### Realization: what actually gets built

Worked out 2026-09. This covers both layers, since "what the compiler emits from a datatype" spans
them; §4 points here.

**No new IR — two annotations on the existing one.** A whole function carries one protocol (per-port
overrides are rejected), and a call to a differently-protocol'd function gets a coercion island whose
ports speak the caller's protocol. So *within* one function every channel has the same handshake
structure, and its presence needn't be represented per channel. Mark **protocol per function** and
**datatype per channel** on today's `Module`/`MutableModule`, and every control wire is derivable:

| control wire | derived from |
|---|---|
| `valid` | protocol — `@valid`, `@ready_valid` |
| `ready` | protocol — `@ready_valid` only |
| `last` | datatype — one per bounded level |
| `phase` | datatype — `seq` |
| branch tag | datatype — `choice` |
| `enable` | coercion structure — where the islands are |

So the high level never models wires; it models the things wires are derivable from. A parallel
representation is unnecessary, and this matches a pattern the codebase already uses for coarse views
(`HierarchicalLeisersonCircuitGraph`, `CombinationalLoopDetector`'s port-reachability summaries).

**Transmitted vs derived.** The useful split among those signals is not by name but by *who holds the
information*:

- **Derived** — computable by the consumer from a counter against the static structure: `phase`, a
  `repeat`'s iteration index, and `last` *when the extent is static*.
- **Transmitted** — the consumer cannot compute it: `last` when the extent is **data-dependent** (a
  `stream`), and a `choice`'s branch tag.

The criterion is §2's static-versus-data-dependent extent axis again. A `stream`'s `last` must arrive on
a wire for the same reason a `stream`'s FSM cannot evaporate at compile time.

**Which settles what crosses module boundaries (decided 2026-09):**

> **A channel carries exactly what the consumer cannot derive, and nothing else.**

So the crossing set is `{valid, ready, last, branch tag}` — the transmitted signals, which cross by
definition since the producer holds information the consumer lacks. **`phase` is re-derived locally**,
per module, by counting `fire`s against the static structure and resetting on `fire && last`. Note a
`choice` makes the phase structure differ per branch, so deriving `phase` needs the branch tag — which
is transmitted anyway, so the dependency is already satisfied.

The alternative — transmitting `phase` — is not free: it would have to stay aligned with its data
exactly as `valid` and `last` do, widening the delay lines those already require by log₂(phases) bits
per stage. Re-deriving costs a counter but *no* delay line, since it is computed at each stage from
signals already aligned there. Both are small, and smaller still because the elastic layer is coarse —
"per module" means per island boundary, not per gate. The single-source-of-truth objection to deriving
in N places dissolves too, since the derivation lives in one stdlib function instantiated N times.

**The lifecycle FSM is a stdlib GAPL function**, not compiler-synthesised nodes. Everything it needs —
a register for the phase, one for the branch tag, comparators, an increment, muxes — is expressible in
GAPL, and it is parameterised by *static* properties of the datatype (phase count, which phase is the
trailing stream, branch count). Lowering a datatype becomes *instantiating* it. That keeps it on §6's
stdlib line (hardware whose source you can read), makes it testable as ordinary GAPL through `sim-test`,
and needs no new synthesis machinery in `NodeBuilder`. The `fire` gating and the lifecycle reset are
both **muxes on the register's input**, so the register stays ordinary and its only special property is
*don't retime me* — and because the FSM is a function, that pinning is expressible per-module rather
than per-register.

Note a stateful function is still `@dataflow`: the rule is that every *port* is `single`, which says
nothing about registers inside.

**`fire` is §2's layer-2/layer-3 interface, not a channel wire.** It is one AND gate computed locally at
each consumer — `fire = valid` under `@valid`, `fire = valid && ready` under `@ready_valid`. Feeding the
FSM `fire` is what makes the same stdlib function work under both protocols with nothing parameterising
the difference, which is §2's swappability claim made concrete.

**The coercion plumbing is three pieces, and one is already built.**

| piece | how |
|---|---|
| the join — `fire = (∧ valid_i) && ready_out`, `ready_i = fire` | stdlib function, parameterised by arity |
| the out-coercion delay — `valid_out = delay(fire, latency)` | stdlib function, parameterised by depth |
| `enable` threading | **already exists** — see §8; every generated module has the port and every register is already gated |

**Ordering: FSM early, coercion delay late.** The lifecycle FSM derives from the datatype and is fully
known at netlist-build time. The out-coercion's delay depth is the island's *latency*, which **retiming
determines** — an island may be combinational before retiming and *k* stages after. So the coercion
plumbing is a **post-retiming pass**. This is precisely why §5 cares about the `VirtualIONode`
constraint: forcing all of a module's outputs to share one lag is what makes "the island's latency" a
single well-defined number rather than a per-path one.

**Open from this work:** how `phase` and the branch tag are *surfaced* to a consumer's body (§10 item
14 — really a grammar question); and two small checks — whether a static `clog2` exists or needs
writing by static recursion, and `if_else`'s actual signature. (Scope-tagging of lifecycle registers,
formerly listed here, turned out to need no mechanism — see "State scoping" in §6.)

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
- **A combinator's protocol is determined by whether any of its ports has temporal extent.** An earlier
  draft claimed rate-*reducing* combinators "force at least `@valid`-like intermittency, hence
  `@ready_valid`" — the conclusion is usually right but the reason was wrong, corrected 2026-09.

  **`@dataflow` requires every port to be `single`** (bare interfaces being implicitly `single`, §4).
  The interlock with layer 2 is then exact:

  - `@dataflow` ⟹ no temporal extent ⟹ no layer-2 state to protect ⟹ `fire` is trivial.
  - extent ⟹ a layer-2 FSM exists ⟹ its state must not advance on a non-beat cycle ⟹ `fire` is
    required ⟹ a handshake is required.

  Which is also why §5 rejects `@valid`: multi-beat-with-no-handshake is exactly the excluded case, and
  it survives only in the hand-written wrapper, where the kernel takes `enable` and no `ready`.

  **The implication runs one way only.** Extent forces `@ready_valid`; an all-`single` signature does
  *not* force `@dataflow`, since a single-beat function containing a buffer may legitimately not be
  ready — §5's own `function g()@ready_valid i1: wire, i2: wire => o: wire` is the example. So this is a
  legality constraint on `@dataflow`, not a derivation of the protocol.

  **Consequently the fold family is two functions with a space/time conversion between them**, not a
  spectrum of protocols. The `@dataflow` fold is the *vector* fold — `fold() i: single(T),
  v: single(U[s]) => o: single(T)`, everything present at once, which is the stdlib's existing
  `combinational_vector_fold`. Anything with temporal extent, `repeat(T, n)` included, is
  `@ready_valid`. You reach the first from the second by converting time into space, which is a
  `regroup`-shaped operation — Aetherling's space-time types (§9), here as a conversion step rather
  than a third protocol.

  This also keeps §5's islands argument intact: a `@dataflow` function cannot contain a stream `fold` or
  `regroup`, because those require extent on a port and a `@dataflow` function has none. It may contain
  a vector fold, which preserves the rate trivially.
- **A combinator's function parameter may not stall — in v1.** Lifecycle position reaches such
  functions as *data*, via the stdlib's existing `last(T)` and `valid(U)` record interfaces, never as
  control. That is the ergonomic bet, and the non-stalling requirement is what lets a combinator promise
  a rate contract at all: if `scan`'s `step` could stall, `|o| = |i|` stops holding.

  **Corrected (2026-09).** An earlier draft of this document stated the rule as "every function
  parameter is pure `@dataflow`," which bundled two claims and justified only one. *Must not stall* is
  justified as above. *Must be beat-level rather than lifecycle-level* is not justified anywhere, and it
  is already false of this design — `oneach(f)` takes `f: stream(T) => stream(U)`, a whole pipeline.
  Nothing requires a combinator's function parameter to be beat-level.

  Moreover the surviving half is the same v1 simplification as "`map` requires a combinational `op`":
  non-stalling is only needed to keep a combinator usable in a `@dataflow` context, since under
  `@ready_valid` a stalling step merely propagates backpressure and slows the combinator down. **The two
  restrictions should lift together when layer 3 lands.**
- **`map` requires a combinational `op`** — **as a v1 simplification, not a permanent rule.** Non-zero
  latency enters only through hard macros (BRAM, DSP, vendor IP), which are latency-declaring channel
  functions, not lifted pure functions. The point is that v1 needs no latency analysis — just "does `op`
  contain registers." Once layer 3 exists, a multi-cycle `op` is expressible, because the handshake
  stalls for it; the restriction should be expected to lift. (Contrast the register-free rule on
  `choice` selectors in §4, which looks similar but has an independent justification that does *not*
  expire.)
- **`map` was overloaded; the per-lifecycle sense is now `oneach`.** Beat-wise `map` takes a
  combinational `op` and generates logic. Per-lifecycle `oneach` takes a whole pipeline and generates
  *nothing* — a module already is a standing process. The overload was never *ambiguous* (it resolves by
  kind: `op`'s argument is an interface in one case, a datatype in the other), but the reader could not
  tell from the source whether a given `map` instantiated a datapath or nothing at all, which is goal 7.
  Making the per-lifecycle case implicit was considered and rejected — no implicit lifting. Note
  `oneach` is a **type-level** operator rather than a combinator: it emits no hardware, so it does not
  belong in the same list as `map`/`scan`/`fold`/`regroup`.

### State scoping, and scanning an infinite lifecycle

**A scan's state is scoped to the lifecycle it is applied to.** Scan over `stream(T)` resets at that
`last`; scan over `infinite(T)` never resets. That much falls out, and it handles MD5's per-packet
padding count directly.

**The initial state arrives as a `single` port, for both bounded and infinite scans** (decided
2026-09, replacing §6's `init: null => S`, which could only seed from a constant — see §10 item 16).
One mechanism, with the scope following from the lifecycle the scan is applied to: a seed on a scan
over `stream(T)` arrives once per bounded lifecycle, while a seed on a scan over `infinite(T)` is a
`single` over the infinite lifetime and therefore lowers to the sticky latch described in §4. That
covers AES-CTR's nonce, a seed parsed from a header field, and the inner scan of the CMS structure
below, which is seeded from the outer scan's state.

Note the seed port is itself an instance of the value-alongside-a-stream relationship deferred in §10
item 2, so it needs an entry in the rate contract table and a `simengine` assertion rather than a type
rule.

**Per-beat updates with cross-lifecycle persistence compose from two features decided separately**, so
no flattening operation is needed. CMS is the motivating case — the sketch must survive across packets
while updating on every beat, meaning its state scope and its step scope are different levels. The
structure:

```text
function cms_netfpga() i: infinite(packet) => o: infinite(packet) {
    // outer scan over the infinite lifecycle: state is the sketch,
    //   and its step consumes a whole `packet`
    // inner scan over that packet's beats: seeded from the incoming sketch,
    //   returning the updated one
}
```

The outer step takes a whole `packet`, which is legal once the function-parameter rule above is
corrected. The inner scan is seeded from the outer scan's state, which is exactly the data-seeded init
in §10 item 16. Between them the case is covered.

> **Corrected 2026-10 — two problems with the above.**
>
> **CMS is per-packet, so this is the wrong structure for it.** Its initial version treats each packet
> independently (confirmed with the author), so it is `oneach` over the packets with a `scan` over each
> packet's beats inside — one accumulator, reset per packet, no outer state at all.
>
> **And the nested structure doubles storage whenever the outer state only round-trips the inner's.**
> The outer register holds the sketch between packets; the inner holds the running sketch during one;
> the outer sits idle for the whole inner lifecycle, then is overwritten. That is two full banks of
> silent hardware. Precisely, the pair is redundant when *both* hold: the outer's next value is exactly
> the inner's final value, *and* the inner's seed is exactly the outer's current value. Then one
> non-resetting register behaves identically. If either side transforms the value, the two registers
> hold genuinely different information and nothing is redundant.
>
> So "no flattening operation is needed" was wrong. Per-beat state that persists across lifecycles
> needs either a flatten from `infinite(stream(T))` to `infinite(T)`, or a standing scan able to
> consume a nested channel at beat granularity. Of the near-path applications only the **token bucket**
> needs it: CMS is per-packet, and HyperLogLog updates once per packet (hashing the header), so it is a
> standing scan over `infinite(packet)` with **no** inner scan — no second bank, no flatten. See §10
> item 17. The nested structure remains correct when the two scopes hold *different* state, e.g. a
> per-packet byte count beside a running total.

**Scope-tagging is a wiring rule, not a mechanism** (settled 2026-10). When a lifecycle ends, which
registers reset? Three existing decisions answer it without any new machinery: `register` is
stdlib-restricted, so every lifecycle-scoped register lives inside a stdlib combinator; each combinator
is applied to one specific channel, whose lifecycle FSM provides `first`; and lifecycle reset is an
init-select mux driven by `first`. So an accumulator's reset is wired to the FSM of the channel its
combinator consumes, and *that* is its scope. A standing scan over `infinite(T)` connects to an FSM
whose `first` fires once, at hardware reset. Pipeline registers inside `@dataflow` islands get no
lifecycle reset, correctly, since they hold in-flight data. The answer is always unambiguous because of
§4's rule that two bounded `stream` inputs to one function is a type error — a combinator has at most
one bounded-lifecycle input. Under nesting, an outer combinator's effective `fire` is "an inner
lifecycle completed," i.e. inner `fire && last`, and the same rule applies.

**A scan over an infinite lifecycle should not share a name with a scan over a bounded one.** They
differ in whether state resets — a semantic difference invisible at the call site, which is precisely
the failure that split `map` from `oneach`. Same reasoning, same remedy. (Note `fold` over an infinite
lifecycle is already a type error per §4, since it never reaches `last` and so never emits.)

**Named `standing_scan`** (settled 2026-10). It stays visibly a scan, reuses vocabulary the doc already
has (§4 calls an all-infinite top-level module "a standing process"), and the extra length marks it as the
rarer case — the short name stays with the common, bounded behaviour. `accumulate` was the alternative;
rejected because it does not look related to `scan`, and English hears it as summation where a
HyperLogLog updates with `max`. Worth noting this split is lower-stakes than `map`/`oneach`: that one
was about **cost** (one generates logic, the other nothing), whereas both scans build the same
accumulator and differ only in whether reset is wired — so this is about **semantics**. `oneach` needs no
rename: it sits on a different axis, being a lifting operator rather than a computation. The resulting
scheme is `map` stateless, `scan` stateful per-lifecycle, `standing_scan` stateful standing, `fold`
reducing per-lifecycle, `oneach` lifting.

**`scan`'s step takes `last(T)` and `fold`'s does not, and that asymmetry is deliberate** (settled
2026-09; it had looked like an oversight). `scan` emits an output *per beat*, so per-beat position is
part of its contract — MD5's padding step relies on exactly this, taking `b: last(md5_beat)` and using
`b.last` to mark the beat that receives the `0x80`. `fold` emits *once*, at an endpoint the combinator
already handles, so its step has no use for position. The apparent counterexamples — CRC32's final
inversion, a one's-complement checksum's carry fold — are "do something different at the end," and read
better as a post-fold operation (`fold(...) => final_xor()`) than as a branch inside the step.

### Rate contracts — stated precisely, checked at runtime, not typed

**Decision (2026-09): rate relationships between ports get no type-level syntax for now.** Length
variables (`stream(T)[n]`, with `[m + n]`, `[max(m, n)]` and so on) were designed and rejected for the
moment — not on principle, but because `choice` makes data-dependent lengths the *common* case for
parsers rather than an edge case, so the system would meet its hardest problem immediately and in the
place it is most needed.

This extends a call §5 already made for `zip`: "no static check, no hardware error handling — but
implement it to deadlock, not desynchronize... Put the actual assertion in `simengine`, where it is
free." The reason it is acceptable here and was *not* acceptable for `@valid` is the failure mode:
`@valid` fails as silently dropped data, whereas a rate violation deadlocks, which §5 explicitly calls
debuggable.

Two conditions attach to the deferral, and they are what keep it reversible:

1. **Every rate requirement carries a `simengine` assertion.** Free, and it converts a documented
   obligation into one that fails loudly in test rather than silently on hardware.
2. **Every rate requirement is written as a spec, not as guidance.** Stated precisely enough to *become*
   a typing rule later, so adding types is mechanical rather than a redesign. Vague prose ("the streams
   should be the same length") would make this decision irreversible; a formula does not.

Writing `|x|` for the beat count of port `x` within one lifecycle:

| mechanism | rate contract | notes |
|---|---|---|
| `join` (generated, arity *n*) | `\|i₀\| = \|i₁\| = … = \|iₙ₋₁\| = \|o\|` | the contract currently asserted **silently**; this is the live bug class |
| `zip` | `\|a\| = \|b\| = \|o\|` | same as join, materialized as data |
| `align` | `\|o\| = max(\|a\|, \|b\|)` | no constraint on inputs — total by construction |
| `merge` (append / round-robin / eager) | `\|o\| = Σ \|iₖ\|` | one mechanism, the policy only decides ordering |
| `select` | `\|o\| = \|i_sel\|`; `\|iₖ\|` unconsumed for `k ≠ sel` | picks one input, does not drain the others |
| `map` / `scan` | `\|o\| = \|i\|` | rate-preserving |
| `fold` | `\|o\| = 1` | rate-reducing |
| `scan_with_tail` | `\|o\| = \|i\|` or `\|i\| + 1` | data-dependent; MD5 padding |
| `regroup(in_b, out_b)` | `\|o\| = ceil(\|i\| × in_b / out_b)` | static ratio only |

**The combining mechanisms, and their duals.** These are five distinct operations, not variants of one:
`join` and `zip` combine *within* a beat (length preserved); `merge` combines *across* beats (lengths
summed) with append, round-robin and eager-by-arrival as policies on one mechanism — note
`axis_3_to_1_arbiter` is already `merge` with the append policy hardcoded 3-way; `select` consumes only
the winner; `align` is the total variant of `zip`. Each has a dual on the fork side — join↔fork,
merge↔positional split, select↔route-by-value — so building the family symmetrically hands you the
split side for free, which is what the header/body split architecture in `applications.md` needs.

**The failure this leaves open**, recorded so it is not a surprise: a `parse` function emitting
`o: stream(beat)` alongside `key: single(five_tuple)` will have a join generated against it that
asserts 1:1, stall after the first beat waiting for a second key, and deadlock — with every fact needed
to catch it statically already present. Until the assertion exists in `simengine`, that surfaces only on
hardware.

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

### The language/stdlib line

> **Lifecycle structure belongs in the language. Field extraction belongs in the stdlib.**

Layer 2's job is temporal agreement and delimiters — `stream`/`seq`/`choice`, `last`, phase — because
that is what producer and consumer must agree on and what nothing else can check. Offsets, straddling,
shifting, and accumulation move out.

This resolves a real tension (efficient-and-visible hardware versus ordinary software-engineering
reuse) along a line the design had already drawn once: §6 restricts `register` to the stdlib rather than
removing it, and requires "an explicitly marked escape hatch for stdlib primitives and foreign IP." A
stdlib function's cost is visible **because you can read its source**. So the line goal 7 draws is not
*compiler generates hardware vs. does not* — it is **hardware you can read the source of vs. hardware
conjured from a type.**

**The evidence this works is already in the repo**, hand-written:

- `netfpga/.../hdl/util/axis/axis_parser.v` extracts a field between static `START_INDEX`/`END_INDEX`
  *of the whole packet*: it tracks `bits_read`/`bits_written`, masks each beat to the contributing
  sub-range, shifts it into position, ORs it into an accumulator, and raises `parsed_value_ready` when
  complete. It handles straddling internally and **never aligns the datapath**. It is a prober — it does
  not drive `tready`.
- `netfpga/.../hdl/util/axis/axis_trim_front.v` is the alignment half, statically parameterized by
  `BYTES_TRIMMED`.

Together they are the extract-into-registers model — the same shape as P4's extract-into-PHV.

**What GAPL needs to write `axis_parser` as a stdlib function**, against what exists today:

| needs | status |
|---|---|
| static parameter arithmetic (`END - START + 1`, comparisons) | exists — MD5 already does `(5 * iteration + 1) % 16` and static `if` chains |
| cross-beat state (`bits_read`, `bits_written`, accumulator) | `register` today, `scan` later |
| dynamic shift by a runtime amount | stdlib has `single_shift_left/right`, `constant_shift_left/right`; a barrel shift is the classic log-depth composition, buildable with existing static recursion |
| mask over a computed range | comparisons + `replicate` |
| a "field complete" flag | `valid(T)`, already in the stdlib |

So it appears to need **no new language primitives** — only the barrel-shifter composition is worth
prototyping before believing. And because `START`/`END` are static while `bits_read` advances by a fixed
stride, the field starts in a statically known beat at a statically known offset, so most of those
"dynamic" shifts are constant-foldable *per instantiation* — something GAPL's static parameters can do
and the hand-written Verilog leaves to the synthesis tool. (`axis_parser.v:78` says as much: "Let's just
do the lazy method. There's a faster way to do this that we can implement if this doesn't meet timing.")

### Where parsing actually gets expensive

| | mechanism | cost |
|---|---|---|
| branching on a parsed value | comparator + FSM state | cheap |
| extent bounded by a length field | down-counter + zero compare | cheap |
| **reading at a runtime-computed offset** | **alignment / barrel shifter** | **the expensive one** |

Almost all of general parsing is affordable provided "read inside a variably-offset region" stays out —
and P4 arrived at exactly that restriction independently, by making `varbit` opaque (see §9). Two ideas
worth taking when the dynamic case is tackled: **length-derived `last`** (a bounded sub-lifecycle whose
extent comes from a parsed value rather than an in-band flag — Kaitai's substream, a down-counter in
hardware), and **opaque variable regions** (carriable and re-emittable, readable only after explicit
normalization). Both deferred; neither blocks the static-header applications.

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
| ~~`@valid` as a surface protocol~~ | **Reversed 2026-09.** Rejected because every safety property was an unchecked obligation with silent data loss as the failure mode — but restricting it to *fork, never join* turns three of the four obligations into rules or checkable conditions. See §5. |
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
- **Nothing in GAPL currently has cross-packet state**, because `axis_mutual_exclusion` drives
  `module_reset` into the kernel between packets (`processor_controller` only gates `enable`). So
  CMS's `register(sketch4(16))` is wiped every packet and accumulates within one packet only.
  **Corrected 2026-10:** an earlier version of this note claimed the code "wants cross-lifecycle state"
  that the deployment cannot provide. That was an inference, and the wrong one — CMS's initial version
  is *intended* to be per-packet, treating each packet independently. So here the deployment and the
  intent agree; it is the absence of cross-packet state *in general* that the serialization imposes.
- **`register` usage in application code** is: per-packet sketch state (`netfpga/src/cms/processor.gapl:29`),
  per-lifecycle feedback (`netfpga/src/regex/processor.gapl:169`,
  `verilator-test/tests/email-with-opt/test.gapl:248`, `gapl-example/src/count-min.gapl:128`), and
  **hand-matched control delay** (`cms/processor.gapl:53-55` delays `valid`/`keep`/`last` by one to
  match the sketch's latency; `regex/processor.gapl:175` registers the whole output interface). The
  third category disappears entirely under this design.
- **`Flattener.Mode`** (`compiler/.../transformer/Flattener.kt:18-22`) is an enum with `NONE` / `ALL` /
  `RECURSIVE`; `RECURSIVE` is the common mode and inlines only *self*-recursive calls, leaving other
  module-invocation calls un-inlined — so there is no single flat netlist to analyze, matching the
  claim in substance.
- **Undriven wires are a hard error, at bit granularity.** `NodeBuilder.validateWiresConnected`
  (`analyzer/.../netlistir/builder/NodeBuilder.kt:121-147`) walks every input wire of every
  `OutputNode`/`BodyNode` and reports `BuilderDiagnosticKind.UndrivenOutputPort` if *any single wire* in
  a group lacks a connection. There is no "it'll be optimized away" path — the compile fails before
  codegen. The check runs per-module during netlist construction, so it has no whole-program liveness
  information (contrast the combinational-loop check, which is whole-program and runs after all modules
  build).
- **There is a hand-written `axis_*` utility library** at
  `netfpga/packet-processor/projects/reference_switch/hw/hdl/util/axis/` — roughly fourteen modules
  including `axis_parser`, `axis_trim_front`, `axis_data_width_converter`, `axis_queue`,
  `axis_hold_metadata`, `axis_3_to_1_arbiter`, `axis_mutual_exclusion`. This is, in effect, the
  hand-written version of the stdlib this design implies, and it is the right baseline to compare
  against. `axis_parser`/`axis_trim_front` are dissected in §6.
- **Retiming erases registers rather than moving them** — established 2026-09, and it refutes a claim
  §5 makes about lowering. `NetlistLeisersonCircuitConverter.fromModule` filters every register node out
  of the graph (`:118`), collapsing register chains into integer edge weights; `toModule` copies every
  body node *except* registers (`:340`); and `addSharedWeightedConnections` mints fresh anonymous ones,
  emitting one shift register per driving wire as deep as its most-delayed consumer and tapping each
  consumer at its own depth. Consequences:
  - **No register identity survives.** "Which source register did this come from" is ill-posed in the
    IR's own terms — source registers are not objects in the graph, only integers on edges. Emitted
    registers aggregate bits from *unrelated* sources that happen to share a driving node, under
    machine-generated names, and retiming may place one where no source register was.
  - **Nothing can be pinned.** `MinimalRegisterSolver` constrains only edge weights, clock period,
    `VirtualIONode` zero-lag, caller-supplied equality constraints, and an anchor. No register-specific
    casing exists in any solver, nor in `Retimer`.
  - So **any per-register attribute is destroyed by retiming** unless it rides on the edge weights *and*
    the shared-shift-register merge refuses to combine bits with differing attributes. Neither exists.
    §5's "the netlist IR never models [enable], so `Retimer`, `CombinationalLoopDetector`, and the graph
    converters keep working unchanged" holds only while all registers are interchangeable, which
    per-lifecycle reset would end.
- **`enable` and `reset` already exist and are already threaded to every register.**
  `PredefinedFunctionNodeCreator.kt` emits, per register, `always @(posedge clock)` with
  `if (reset) r <= 0; else if (enable) r <= next; else r <= r;`. So §5's lowering plan for `enable` —
  "a port on every generated module… every `always @(posedge clk)` becomes `if (enable)`" — is **already
  implemented**, which is why `gapl_wrapper.v` can pass `.enable` into the kernel today. What does not
  exist is a per-register *choice* of reset value for ordinary registers; the literal zero is hardcoded.
- **There is precedent for a register carrying extra state, and evidence it has never survived
  retiming.** `PredefinedFunction.kt` defines two variants: `RegisterFunction` (a bare one-cycle delay)
  and `IntegerRegisterFunction`, which carries a `default` emitted as its reset value. But
  `IntegerRegisterFunction` is excluded from the retiming graph (via `isRegister`, which covers both)
  while *not* excluded from `toModule`'s copy (which tests `!is RegisterFunction` only) — so such a node
  would be copied into the retimed module with nothing reconnecting it, its connections having been
  erased into weights. Currently unreachable, since no `.gapl` file in the repo instantiates
  `integer_register`, but it should be fixed on its own account.
- **Realignment is expensive to hand-write even for a fixed offset.**
  `netfpga/packet-processor/lib/hw/contrib/cores/vlan_remover_v1_00/hdl/vlan_remover.v` is 415 lines: a
  seven-state FSM (one state commented "Realigning packets"), a 4-deep input FIFO, and a 512-bit
  holding register concatenating the previous beat with the current one to slice a 256-bit window out
  (`ns_tdata = {32'd0, fifo_axis_tdata, tdata[223:0]}`) — all for a **hardcoded 4-byte** shift. There is
  a matching `vlan_adder`. That window structure is the same one hardware packet parsers use generally
  (§9).

### The hand-written FSM corpus

Surveyed in the 2026-09 session across `gapl_wrapper.v`, `util/**` and `packet_processor/**` — every
finite state machine the author wrote by hand. This is the most direct evidence available of what the
language needs to absorb, because **these modules are in Verilog precisely because GAPL could not
express them.** Sixteen state machines (8 with named states, 8 implicit — counters or flags driving
distinct phases), plus the three that MD5 and AES-CTR would add.

| FSM kind | instances | covered by | status |
|---|---|---|---|
| **Position** within a lifecycle | `packet_packer` (eth→ip→udp→body), `packet_processor` (header vs body), `axis_mask_back`, `axis_trim_front`, `variable_width_queue`'s first-beat flag | `seq` + compiler-derived phase/`first`/`last` | designed |
| **Accumulation** across a lifecycle | `axis_parser` (field assembly), MD5's byte count, AES-CTR's counter | `scan` state, auto-reset on `last` | designed |
| **Conditional trailing emission** | `axis_pad_output`, MD5's extra padding block | `scan_with_tail`'s `tail: S => valid(U)` | designed |
| **Per-lifecycle reset by serialization** | `axis_mutual_exclusion`, `packet_parser`'s nine-parser reset | `scan`/`fold` reset on `last` — *deletes* these | designed |
| **Flow control / stall** | `processor_controller`, `fallthrough_variable_width_queue` | layer 3 `@ready_valid` | deferred |
| **Value held alongside a stream** | `axis_hold_metadata`, `axis_user_tracker` (two FSMs) | — | **gap** |
| **Multi-stream merge / interleave** | `axis_3_to_1_arbiter`, `axis_transmission_combiner` | — | **gap** |
| **Rate adaptation** | `axis_pad_output` | partial — needs the output lifecycle's length declared | **gap** |

Rows 1 and 2 are both `scan`-shaped but differ in **who writes the state**: position is derived by the
compiler from the lifecycle type and costs the user nothing to declare; accumulation is user-declared,
with the accumulator's width visible in the signature. That is the derived-predicate/`scan`-state line
from §4, confirmed against real code.

**Tally:** of nineteen FSMs, roughly ten are covered by what is designed, two or three need layer 3, and
**six are the rate-relationship gap** — which §10 item 2 had filed as orthogonal and safely deferrable.
Six hand-written modules say otherwise. Two further findings worth keeping:

- **Not one iteration FSM exists in the corpus.** No "which round am I on" counter anywhere, and neither
  MD5 padding nor AES-CTR chaining introduces one. Time-multiplexing is a real future want (§10 item
  10) but it is not what the hand-written FSMs are doing.
- **MD5 and AES-CTR add no new FSM *kinds*** — both land in rows 2 and 3. But *deployed*, both hit the
  gap rows: running MD5 for real means holding parsed header fields alongside the body stream while the
  kernel works, which is exactly what `axis_hold_metadata`/`axis_user_tracker` were built for.

### The wrapper is a list of missing features

A second way to read the same corpus. `gapl_wrapper` layers the generated kernel in adapters, and each
one exists because the kernel lacks a capability:

| layer | exists because the kernel... | goes away when... |
|---|---|---|
| padder (`axis_pad_output`) | can't emit a variable number of output beats | the output lifecycle's length is declared |
| mutex (`axis_mutual_exclusion`) | has no per-lifecycle state reset | `scan`/`fold` reset on `last` |
| controller (`processor_controller`) | has no `ready`, only a global `enable` | layer 3 lands (§5 already names this one) |
| byte reversal (`reverse_bytes`) | uses a different byte convention than the bus | never — a genuine adapter, belongs in the stdlib |

A useful test for whether a future layer is an adapter or a workaround: *would it still exist if the
kernel were hand-written Verilog?* For these four: yes, no, yes-if-non-elastic, yes.

**The padder's real origin** (from the author, 2026-09): an earlier version used `packet_parser` and
`packet_packer` to decompose and rebuild packets, but that workflow could not be debugged and was
abandoned — which is why both modules are now dead code. The replacement assumes the header is **always
two beats**, achieved by padding the front of the body, and leaves the original header untouched. That
forces the packet length to stay constant, hence padding the output back up to the input's beat count,
and it forces the checksum to be omitted. So the padder is downstream of an abandoned architecture, not
a considered design choice, and the author wants to back out of it.

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

**Packet parsing specifically** (surveyed in the 2026-09 layer-2 session; sources checked, not recalled)
- *Design Principles for Packet Parsers* — Gibb, Varghese, Horowitz, McKeown, ANCS 2013 (best paper).
  Hardware parsers **consume the packet in fixed-size chunks per cycle**, and fields straddling a chunk
  boundary are handled by multiplexing over a window spanning the previous and current chunk, with
  alignment circuitry to extract a field regardless of its position in that window. Ships a parser
  generator emitting synthesizable Verilog. *Architecture confirmed from the paper; the quantified area
  numbers were not extracted and are worth reading first-hand.*
- **P4.** `varbit`'s width is inferred as `(8 * length) - sum_of_fixed_fields`, at most one per header,
  and the length must be known **before** extraction, from earlier fixed-width fields. The restriction
  list is the informative part: you may emit a varbit unchanged in the deparser, `setInvalid()` it, copy
  a whole header of identical type, or assign from another varbit of matching max width — you may
  **not** use it as a table key, do arithmetic, compare, do bitwise ops, or extract/modify any of its
  bits. So P4's answer to dependent offsets is *let the region exist but make it opaque*, because
  reading bit *k* at a runtime offset needs a dynamic mux. Header **stacks** handle regularly-formatted
  repetition (VLAN, SRv6) as "1 to N instances of identically-sized portions," N statically bounded —
  independent confirmation of the DAG-only + bounded-repetition call above. Deparsing is explicit
  (`packet.emit(hdr)`, conditional on a per-header **validity bit** — literally `valid(T)`), which is
  how P4 avoids output alignment: emit the valid headers in order, then the residual payload.
  *A wart to avoid:* because varbit is so restricted, P4 users define two header types, one with varbit
  for parsing and one without for construction.
- **Kaitai Struct.** Declarative binary formats. **Substreams** are the idea worth stealing: `size` on a
  type reference creates a bounded sub-stream the inner type physically cannot read past, and
  `size-eos` inside means "end of *my* allotment" — so the inner type is written once and reused
  bounded or not. Also `switch-on`/`cases` (spatial choice on a parsed value), `if` (conditional
  presence), three `repeat` forms, and `params`/`args`. Its explicit negative is `instances` with
  `pos:` — random access, lazy, and documented as *incompatible with forward-only parsing*. That is the
  line streaming hardware cannot cross.

**Note on P4's model vs. GAPL's.** P4 is target-dependent: on fixed-function ASICs the compiler
*configures* an existing programmable parser (allocating parser states, packing the PHV) and **fails to
compile** if the program exceeds the budget; on FPGA targets it compiles to HDL. GAPL is unambiguously
in the generation camp. The consequence is that P4's restrictions are a mix of two kinds and only one
transfers: *cost-driven* ones do (varbit opacity, bounded repetition, length-known-before-extraction —
which is just §2's causal-and-local condition restated), while *budget-driven* ones do not (max parser
state count, PHV width, one-varbit-per-header). The deeper structural difference: **P4 hides beats,
GAPL exposes them.** A P4 parser is an imperative state machine the programmer writes (`state` blocks,
`transition select`), and chunking is a target detail the compiler handles; a GAPL `datatype` is a
declarative format description from which the FSM is derived. Kaitai is the stylistic cousin, P4 the
domain cousin — and P4 therefore offers no precedent for deriving an FSM from a format, which is why
the pointer above is to Brzozowski rather than to P4.

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

1. **Alternation in the lifecycle language** (§4) — **semantics now decided; syntax and one typing rule
   remain.** The 2026-09 session settled DAG-only, spatial-vs-temporal, spatial alternation as a
   language construct, selector provenance and cost, the mandatory `else` with don't-care sugar,
   selectors as register-free `expression`s, non-reconvergent branches, and nesting. See §4. What is
   left:
   **Reshaped (2026-09): `choice` exists, but carries no selector — the producer chooses.** See
   "Alternation: the producer chooses" in §4. That settles the semantics and eliminates the mandatory
   `else` and the selector-provenance analysis entirely, since a producer can only pick a branch the
   type declares. It also resolves `null`'s optional-trailing-phase spelling. What remains of this item:
   - **Surface syntax**, for `view`, `datatype`, and the boundary keyword — folded into the grammar
     work that has to happen before implementation regardless.
   - ~~**Datatype compatibility: nominal or structural?**~~ **Resolved (2026-09): nominal.** Aliases
     transparent, separate declarations distinct, no new syntax required since distinctness is the
     default and GAPL already has aliases. Deliberately stricter than layer 1, whose width-only checking
     is the problem being solved rather than a convention to preserve. See "Compatibility is nominal" in
     §4 — and note this also removes the symbolic-automata work from the project entirely.
   The overlay hint that used to sit here is superseded: variants share a wire budget through **views**
   (§3), authored rather than optimized into place.
2. **Rate relationships between two ports have no syntax.** Blocks two-stream joins, `zip`, and any
   `regroup` whose ratio is not statically known. A generated join silently asserts 1:1, and violation
   manifests as deadlock rather than a type error. **Classification (2026-09):** the *enforcement* is
   layer 3 (the join, the handshake), but the *specification* is layer 2, since a rate relationship is
   stated purely in beat counts and is stall-invariant. Pushing the whole concern to layer 3 would
   repeat the mistake of leaving `last` in the interface. The gap is narrower than it reads in one
   respect: static single-in/single-out ratios already work, expressed in the beat types
   (`regroup(32, 64)` is `stream(beat(32)) => stream(beat(64))`); what is missing is ratios not
   statically known, and relationships between two separate ports.

   **Promoted to the top of this list by the FSM corpus survey (§8).** This was previously filed as
   "orthogonal to the datatype grammar, safe to defer." It is not: **six of the nineteen surveyed FSMs
   are this gap** — more than layer 3 accounts for — and it is the only category with no design answer
   at all. Concretely it blocks: holding parsed header fields alongside a body stream (`axis_hold_metadata`,
   `axis_user_tracker`), merging or interleaving multiple streams (`axis_3_to_1_arbiter`,
   `axis_transmission_combiner`), and rate adaptation (`axis_pad_output`). Critically, it is on the path
   for **both** named target applications: deploying MD5 means holding headers while the body is hashed,
   and AES-CTR needs the keystream aligned with the plaintext once AES is pipelined.

   The shape the author wants to write (2026-09): split a packet stream into a header stream and a body
   stream, transform each independently, then concatenate and repack to remove the dead space between
   them. That is a positional demux, a `seq`-style concatenation of two independently-produced streams,
   and a gearbox — all three of which are this gap. It is also `packet_packer`'s architecture
   generalized, and the packing is *statically* determined when the header sizes are constants, so it is
   meaningfully easier than the dynamic-offset case in item 15.

   **Resolved for now, deliberately, without types (2026-09).** Length variables were designed and set
   aside: `choice` makes data-dependent lengths the common case for parsers, so the system would meet
   its hardest problem immediately. Instead each combinator's rate contract is **stated as a formula**
   and **asserted in `simengine`** — see §6, which carries the contracts. This is the same call §5 made
   for `zip`, and it is acceptable here only because rate violations deadlock rather than silently
   dropping data. The two conditions are what keep it reversible: precise specs become typing rules
   mechanically, vague prose would not.

   **If it proves insufficient, the cheapest next step is 1:1 only** — no arithmetic, no sums or maxes,
   no data-dependent lengths, no solver, just a way to say "these two ports advance together." That
   would not express `merge` or the parser's key, but it would make the generated join's silent 1:1
   assumption checkable, which is the actual bug class.

   **The compiler-generated join is less exposed than it first appears.** A join at a coercion site
   synchronizes the coerced function's input ports, and for everything currently legal that assertion is
   already safe: all-`single` signatures satisfy 1:1 trivially, `single` + `stream` is handled by §4's
   sticky latch, and two bounded `stream` inputs are *already a type error* under §4's invocation-boundary
   rule. What remains is two `infinite` ports, which is a deliberate authoring choice like writing `zip`
   and gets the same treatment. The residual concern is therefore diagnostic rather than safety — a
   failure points at a generated join instead of at user code — which is item 9 below, not a separate
   problem.
3. ~~**`map` is overloaded**~~ — **resolved (2026-09):** the per-lifecycle sense is now `oneach`, a
   type-level operator that emits nothing; `map` keeps the beat-wise, logic-generating meaning. See §6.
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

   **Deferred until after the typing work (2026-09), and cheaply so.** An unretimed island is
   *combinational* — retiming is what inserts pipeline registers, and `register` is stdlib-restricted so
   user datapath carries none of its own. So before retiming an island has latency 0 and the
   out-coercion's `valid_out = delay(fire, latency)` collapses to `valid_out = fire`. The post-retiming
   ordering constraint in §5's "Realization" only bites once retiming is in play. Existing designs are
   untouched: today's MD5 retimed configurations contain no lifecycle state. What is deferred is
   retiming *new*, lifecycle-using designs.

   > **What keeps the deferral cheap, and must not be casually reversed:** module-level retiming
   > exclusion is only available because the lifecycle FSM is a **stdlib function** — a `ChildGraphNode`
   > the hierarchical converter can decline to descend into. Synthesise the FSM inline into its parent
   > instead and that route closes, leaving only the harder lag-constraint approach.

   **Much of the rest is addressed; two things are not.** Each mechanism is designed *separately* (§5
   makes `enable` a port applied at serialization; §4/§6 make `scan`/`fold` state reset per lifecycle).
   Of the three difficulties below, the *ordering* one dissolves — using the lifecycle FSM's own `first`
   output to select `init` is correct whether a combinator reads its result combinationally or from a
   register, and costs nothing since the FSM computes `first` anyway. The *three mechanisms* difficulty
   also largely dissolves: hardware `reset_n` is already handled and already not enable-gated, `fire`
   gating is the existing module-level `enable` port, and lifecycle reset is an init-select mux. What
   remains is the retiming interaction, and a check on whether `enable` can be driven per-instance
   rather than only threaded down uniformly.

   **Verified 2026-09, and it corrects an assumption:** `IntegerRegisterFunction` is **not** excluded
   from retiming and is not a precedent to follow. `isRegister` (`PredefinedFunction.kt:243-244`) covers
   both variants, so `isRegisterNode` treats it identically to a plain register — erased into edge
   weights at `NetlistLeisersonCircuitConverter.kt:55/63/119`. Retiming then redistributes those weights
   and mints plain `RegisterFunction`s at `:202`, so the `default` is **silently lost**; and the
   original node is *also* copied into the rebuilt module, because `toModule`'s filter at `:340` tests
   `!is RegisterFunction` specifically — leaving a dangling node whose connections were already erased.
   Unexercised only because nothing in the repo instantiates `integer_register`. **Worth fixing on its
   own account**, independently of this design.

   Note the obvious repair does not work either: making `isRegister` false would move such a node into
   the *non*-register branch, where it is scored as combinational — so the clock-period constraint would
   try to fit a path that really does contain a clock edge, and a feedback loop through a lifecycle
   register would present as a zero-weight cycle, which `LeisersonCircuitGraph`'s constructor rejects
   outright. What is needed is a third category the model cannot currently express: a node contributing
   register delay whose weight is *pinned*. Two routes — constrain the lag difference across it
   (`r(sink) == r(source)`, expressible through `MinimalRegisterSolver`'s existing
   `additionalEqualityConstraints`), or exclude at module granularity per the note above.

   The three original difficulties, for the record:

   - **Ordering.** "Reset on `last`" is imprecise. If the reset lands on the cycle the last beat fires,
     that beat's contribution is wiped before `fold` can emit it. It has to take effect at the *first
     beat of the next lifecycle* instead.
   - **Three mechanisms, not two.** Hardware `reset_n` overrides everything and must *not* be
     enable-gated; lifecycle reset *must* be enable-gated, since a `last` presented during a stall has
     not fired and must not reset anything; `enable` holds. `if (enable) state <= (lifecycle_reset ?
     init : next)` handles the latter two and says nothing about the first.
   - **They live at different levels of the compiler.** §5 deliberately keeps `enable` out of the
     netlist IR so `Retimer`, `CombinationalLoopDetector` and the graph converters keep working — but it
     also says `valid`/`ready`/`last` *do* become real netlist wires before the transformer pipeline.
     Lifecycle reset derives from `last`, so it is modelled; `enable` is not. The IR therefore sees only
     part of each register's update rule. Worse, lifecycle reset applies to `scan`/`fold` accumulators
     but **not** to ordinary pipeline registers, so retiming moving a register across that boundary
     would change semantics — and retiming currently assumes the registers it shuffles are
     interchangeable. This is a *second*, distinct reason accumulators are special to retiming; the
     first is `md5-lifecycle-sketch.md` finding 3, where the fold loop cannot be pipelined at all.
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
    **Downgraded in urgency (2026-09):** `@valid` is now a surface protocol, so its efficiency can be
    written directly rather than recovered automatically. The pass remains desirable — the compiler
    proving the unsafe thing safe still beats the user asserting it — but it is no longer the only route
    to that performance.
12. ~~**Per-port protocol overrides.**~~ **Rejected (2026-09), not deferred.** The motivating case — a
    configuration value beside a stream — is served by lifecycle choice rather than protocol choice: a
    `single` over an `infinite` lifetime, lowered to §4's sticky latch. A function carries one protocol.
    See §5.
13. **Compile time.** Flagged during the 2026-09 merge as unconfirmed: the original claim was that
    `ModuleBuilder.kt` carries an open `TODO` noting an unexplained ~72s compile time on
    `verilator-test/tests/aes/test.gapl`. That TODO does not exist. What `ModuleBuilder.kt:102-114`
    actually documents is a *resolved* historical bottleneck — 696s down to ~0.05s, caused by
    `CombinationalLoopDetector`'s old flattening approach and already fixed. Whether layer-2/3 work
    reintroduces a compile-time concern worth tracking is genuinely open, but the specific evidence
    cited for it was wrong.
14. **Field-access syntax.** The *mechanism* is settled — current-phase access only, via the phase's
    view; cross-phase carry via `scan` state; lifecycle position reaching pure functions as data through
    wrapper records (§4, §6). The concrete surface syntax is not written down.
15. **The dynamic-offset path**, deliberately deferred as a package: a stdlib `parse(START, END)` over a
    stream (§6), **length-derived `last`** (a bounded sub-lifecycle whose extent comes from a parsed
    value — Kaitai's substream, a down-counter in hardware), and **opaque variable regions** (P4's
    varbit discipline). None of it blocks the static-header applications; all of it is needed for a
    general parser. Note this is the same machinery as dynamic `regroup` in (2), so deferring one
    largely defers the other.

    **Scheduling clarified (2026-09).** `header-parser-sketch.md` finding 5 says the stdlib `parse` is
    needed for *static* headers too, because protocol fields straddle beat boundaries — which appeared
    to contradict this item's deferral. The two were disagreeing about *when*, not *whether*: `parse`
    is deferred relative to the language changes, and required before the header-parser application is
    attempted. The rest of item 15 — length-derived `last`, opaque variable regions — stays deferred.
16. **`scan`/`fold` cannot seed the accumulator from data.** §6 types `init: null => A` — a constant.
    AES-CTR's counter starts from a nonce or IV that arrives as data; the round-level fold in a
    time-multiplexed core would need to seed from the incoming block; and any `scan` whose initial state
    depends on a parsed header field needs it. Small, concrete, and it has now come up three times from
    unrelated directions, which usually means the signature is wrong rather than the use cases being
    exotic. **Resolved (2026-09):** the initial state arrives as a `single` port, for both bounded and
    infinite scans, with its scope inherited from the lifecycle the scan is applied to. See "State
    scoping" in §6.
17. **Round-tripping outer state, and the beat-granular standing scan.** *Not a blocker; deferred
    2026-10.* When an outer state only round-trips an inner scan's state — outer's next value is exactly
    the inner's final value, and the inner's seed is exactly the outer's current value — the two
    register banks are redundant, and one non-resetting register behaves identically. See the
    correction under "State scoping" in §6.

    *Detecting it:* the general question ("is this register redundant with that one?") is sequential
    equivalence checking, expensive and not worth attempting. But this specific pattern is trivially
    syntactic **at the combinator level, before lowering** — an inner `scan` seeded straight from the
    outer state, inside a step returning the inner's final state straight back. After lowering it is
    much harder, since the reason the banks are equal is gone.

    *Optimising versus linting:* a silent rewrite is possible but sits badly with goal 7 — the source
    implies two banks and the compiler builds one — and, worse, it creates a **cliff**: insert any
    non-identity transform on the round trip and the pattern stops matching, so area silently doubles
    by the full width of the state. Leaning instead toward making the efficient form *writable* and
    using the detection as a **lint** ("this outer state only round-trips the inner scan's state; write
    it as a standing scan"), which keeps source and hardware in agreement. §7's point that developers do
    not read warnings applies, but less sharply: ignoring this one costs area, not correctness.

    *The efficient form* is a standing scan able to consume a nested channel at beat granularity (or a
    flatten from `infinite(stream(T))` to `infinite(T)`). It is **independently required by the token
    bucket** (`applications.md` #6), which keeps per-beat state across packets — so one feature serves
    both. Not needed by CMS (per-packet) or HyperLogLog (one update per packet).
18. **Tightening layer-1 connection checking.** *Deferred from this round, 2026-10.* Replacing today's
    width-only check with nominal checking is the only breaking change in this effort and needs its own
    design (slices, anonymous `wire[N]`, view fields). Migrate warning-first. See "Compatibility is
    nominal" in §4.
19. **Consumer output on a truncated lifecycle.** *Deferred 2026-10.* Resync on early `last` is settled;
    whether a consumer emits, drops, or flags its output is not. Lean: emit by default, flag via `choice`
    as opt-in, never drop. Also open: whether the compiler should warn when a `seq` consumer ignores
    truncation. See "`last` returns the FSM to its initial state" in §4, and `header-parser-sketch.md`
    finding 4.

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
