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

A **view** is a named reinterpretation of an interface's bits — a second set of names bound to bit
ranges of an existing value, not a new value.

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

- **Views produce no ports.** A view is slicing applied to a base-interface value inside a body, not a
  port type of its own.
- **Structural, not bit-flat.** A view is defined over the base's named fields and their slices, never
  over its flattened bit string — otherwise you can write a view cutting incoherently across `data` and
  `keep`, which is never meaningful.
- **Partial views are legal, and the obligation is asymmetric.** *Reading* through a view is a
  **projection** — name the bits you care about, no obligation for the rest. *Writing* through a view is
  a **construction** — you must produce a complete value of the base, so unnamed bits are an obligation
  you have not discharged. Partial view + output is an error unless the view declares a fill.
- **Unnamed bits are don't-care**, not zero. Zero-filling silently asserts a value someone may come to
  depend on. `left_pad` in the stdlib is the existing idiom for an explicit fill
  (`literal(padding, 0) => o[original:original + padding - 1]`).
- **No implicit widening** from a view-typed lifecycle to its base. This is what makes don't-care
  *safe* rather than merely honest: no type grants access to the unnamed bits, so nothing can read
  them. An explicit widen is where a fill gets declared.
- **External (hardware) interfaces may not use views.** Their datatype is over a plain GAPL interface.
  This is what removes the correctness concern entirely — undriven bits can never reach a real bus,
  where "nobody reads them" would be false. A boundary keyword marks these; see §4.

**Undriven wires are a hard error today, at bit granularity** — `NodeBuilder.validateWiresConnected`
(`analyzer/.../netlistir/builder/NodeBuilder.kt:121-147`) reports `UndrivenOutputPort` if *any single
wire* of an output group lacks a driver. So "the unused bits will just be optimized away" is not the
current behaviour; the compile fails before codegen. The rules above are what keep that check intact.

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
choice(sel) { ... }// alternation — see below
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

**A scan over an infinite lifecycle should not share a name with a scan over a bounded one.** They
differ in whether state resets — a semantic difference invisible at the call site, which is precisely
the failure that split `map` from `oneach`. Same reasoning, same remedy. (Note `fold` over an infinite
lifecycle is already a type error per §4, since it never reaches `last` and so never emits.)

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
- **Nothing in GAPL currently has cross-packet state**, because `axis_mutual_exclusion` drives
  `module_reset` into the kernel between packets (`processor_controller` only gates `enable`). So
  CMS's `register(sketch4(16))` is *written* as a persistent sketch but is wiped every packet — it
  accumulates within one packet only. The code wants cross-lifecycle state; the deployment cannot
  provide it. This is a known consequence of the serialization, not a bug report, and it is one more
  thing that per-lifecycle `scan` reset would fix by making the intended scope sayable.
- **`register` usage in application code** is: intended-cross-lifecycle state (`netfpga/src/cms/processor.gapl:29`),
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
   - **Surface syntax**, for `view`, `datatype`, `choice`, `else`, and the boundary keyword — folded
     into the grammar work that has to happen before implementation regardless.
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

   **Nothing here is decided** — each mechanism is designed *separately* (§5 makes `enable` a port
   applied at serialization; §4/§6 make `scan`/`fold` state reset per lifecycle), and how they compose
   on one flip-flop is open. Three difficulties, expanded 2026-09:

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
16. **`scan`/`fold` cannot seed the accumulator from data.** §6 types `init: null => A` — a constant.
    AES-CTR's counter starts from a nonce or IV that arrives as data; the round-level fold in a
    time-multiplexed core would need to seed from the incoming block; and any `scan` whose initial state
    depends on a parsed header field needs it. Small, concrete, and it has now come up three times from
    unrelated directions, which usually means the signature is wrong rather than the use cases being
    exotic.

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
