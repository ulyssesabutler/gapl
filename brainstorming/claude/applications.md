# Candidate Applications

A roadmap of applications to implement in GAPL, with feasibility keyed to the known gaps in
`type-system.md` §10. The blocked ones are as useful as the easy ones — they are forcing functions for
gaps you already know about.

Existing applications in the repo: AES, MD5, CMS (count-min sketch), regex/email matching, priority
router, mux-demux. ClickNP (Li et al., SIGCOMM 2016) is a useful reference point — its application set
(OpenFlow firewall, IPSec gateway, L4 load balancer, pFabric scheduler, packet generator/capture) maps
closely onto what follows.

| # | application | feasible now? | tests / blocked on |
|---|---|---|---|
| 1 | Fixed-protocol header parser | yes | `seq` heterogeneous phases |
| 2 | CRC32 / Ethernet FCS | yes | `fold`, without MD5's Fmax problem |
| 3 | AES-CTR | yes | contrast case: pipelineable recurrence |
| 4 | L4 load balancer | yes | `seq` + in-flight rewrite + config latch |
| 5 | Match-action classifier | yes (static rules) | parameter system; area scaling |
| 6 | Token bucket policer | yes | `scan` over `infinite` |
| 7 | HyperLogLog | yes | validates `scan` replaces raw `register` |
| 8 | Bloom filter | **no** | forces the memory primitive |
| 9 | 2D stencil / line-buffered filter | **no** | forces memory *and* lookahead |
| 10 | Round-robin scheduler / arbiter | **no** | forces the rate-relationship gap |
| — | Multi-protocol parser | **no** | forces alternation (gap 1) |

**If picking three to do next:** header parser (validates the untested half of layer 2), CRC32
(validates `fold` without the Fmax confound), HyperLogLog (validates the `register` restriction against
the existing CMS).

---

## Tier 1 — feasible now

### 1. Fixed-protocol header parser (Ethernet + IPv4)

Extract MACs, IPs, ports, protocol, payload length at **fixed byte offsets**. The purest test of `seq`
with heterogeneous phases — header beats and body beats on the same wires meaning different things —
which is the one layer-2 feature MD5 does not exercise at all. No memory, no rate relationships, no
recurrences.

> **Unverified.** The original note here claimed this was "already half-sketched in the `Brainstorming
> Interfaces` PDF." No file by that name, and no header-parser sketch matching this description, could
> be located anywhere in the current tree (`brainstorming/archive/interfaces.md` and
> `brainstorming/archive/protocols.md` were checked — neither has it) during the 2026-09 merge. Flagged
> rather than silently dropped or pointed at the wrong file.

**Deliberately fixed-protocol.** A *general* parser needs data-dependent branching (P4's
`select(etherType)`), which is the alternation gap. Splitting the target this way keeps the immediate
version feasible while making the general version an explicit, scheduled piece of work.

**Shape settled in the 2026-09 layer-2 session.** The parser converts
`infinite(stream(axi_beat))` → `infinite(packet)`, where `packet` is a datatype of the form
`seq { header1: single(V1); header2: single(V2); headerbody: single(V3); body: stream(V4) }` and every
`Vn` is a **view** of the one base interface `axi_beat` (`type-system.md` §3–§4). So views are not
optional decoration for this application — the one-base-per-datatype rule makes them the mechanism that
lets a `seq` have heterogeneous phases at all. The conversion itself generates only the phase-tracking
FSM: same base, re-typed temporal structure, no datapath.

Two things this application will run straight into:

- **Headers do not align to beat boundaries.** An Ethernet header is 14 bytes and an IPv4 header 20, so
  on a 32-byte beat they share beat 0 and the 5-tuple straddles beats 0 and 1. A phase's view therefore
  routinely covers the tail of one protocol header and the head of the next — which is why the view
  should be a struct of structs, and why **spatial alternation** (choosing an interpretation from a
  field in the *same* beat) is needed alongside `choice`. See `type-system.md` §4.
- **The fixed-offset restriction is load-bearing, not a shortcut.** Assuming IHL = 5 and one VLAN case
  is what keeps the number of view variants enumerable. Lifting it makes the body's start offset a
  computed value, at which point the layout depends on a term and you need either normalization (a
  barrel shifter, with the offset carried as a value) or the deferred dynamic-offset package in
  `type-system.md` §10 item 15.

### 2. CRC32 / Ethernet FCS

A `fold` over the packet — same shape as MD5, but the per-beat step is a linear XOR network roughly ten
levels deep instead of 64 chained MD5 rounds. **That matters: it is the same structural pattern without
finding 3's Fmax cliff**, so it proves the `fold` lowering works before C-slow exists. The checksum
example already sits in `brainstorming/archive/examples/`.

### 3. AES-CTR

The AES core exists. Counter mode is the deliberate contrast to MD5: successive blocks are
*independent*, so there is no accumulator loop and retiming can pipeline it arbitrarily deeply. The
counter itself is a `scan` over the infinite stream. Running MD5 and AES-CTR side by side gives a clean
before/after on exactly the recurrence problem — same framework, one hits the cliff and one does not.

(GCM adds a GF(2^128) multiply-accumulate, which *is* a chained recurrence and does hit the cliff.)

### 4. L4 load balancer

Parse the 5-tuple, hash it, pick a backend, rewrite the destination. Three things at once: `seq`
phases, modifying the header while the body passes through untouched, and a backend table supplied as
`single(table)` over an `infinite` lifetime — the first real use of the sticky-latch config path. Small
enough to be tractable, realistic enough to be worth publishing.

---

## Tier 2 — feasible, but stresses something

### 5. Match-action classifier

Entirely combinational, which is why it is feasible. For *N* rules: *N* parallel comparators, a
priority encoder picking the lowest-index hit, a mux selecting that rule's action. Ternary match is
`(key ^ value) & mask == 0`.

In GAPL this is the static-recursion idiom already used by `replicate`/`reverse`/`index_list`, and
fall-through recursion gives priority ordering for free:

```text
interface rule(key_bits: integer, action_bits: integer) {
    value:  wire[key_bits];
    mask:   wire[key_bits];
    action: wire[action_bits];
}

function ternary_match(key_bits: integer)
    key: wire[key_bits], r: rule(key_bits, 1) => hit: boolean
{ /* (key ^ r.value) & r.mask == 0 */ }

function classify(n: integer, key_bits: integer, action_bits: integer)
    key: wire[key_bits], rules: rule(key_bits, action_bits)[n]
        => action: wire[action_bits], hit: boolean
{
    // match rules[0]; if hit take its action,
    // else recurse on rules[1 : n-1]  — static recursion, runtime mux
}
```

Static structure, dynamic selection — the same pattern as `left_rotate_for_iteration`, except that one
resolves entirely at compile time and this one leaves muxes behind.

**Where the key comes from.** For a fixed protocol stack the 5-tuple lives at fixed byte offsets:

| field | byte offset | beat (32B beats) |
|---|---|---|
| IP protocol | 23 | 0 |
| src IP | 26–29 | 0 |
| dst IP | 30–33 | **straddles 0/1** |
| src port | 34–35 | 1 |
| dst port | 36–37 | 1 |

So key extraction is constant slices spanning the first two beats plus **one beat of carry state**. Not
parsing — slicing. Fully feasible today, and it is the *same prerequisite* as application 1, so doing
the fixed extractor once serves both.

Caveats are the places fixed offsets stop being fixed: IPv4 options (IHL > 5 shifts TCP), VLAN tags
(shift by 4), IPv6 (different layout). Real hardware commonly assumes IHL = 5 and handles one VLAN case.

**One structural detail to design in from the start:** the key is not complete until beat 1, but beats 0
and 1 have already left by then. So the packet must be **delayed by as many beats as the key spans** —
two beats of registers, 512 bits, nothing — so the action is available before beat 0 exits. That delay
is a fixed-latency rate-preserving shift, and `valid`/`last` stay aligned with it automatically via the
`VirtualIONode` lag constraint.

**Where the rules come from — the real catch.**

- **Compile-time constants**: feasible today, zero new features, fine for a demo.
- **Runtime-configurable**: what makes it a real classifier, and **GAPL has no control-plane concept at
  all**. In real hardware the rules arrive over AXI4-Lite — address, write-enable, request/response.
  Information flow reverses by phase, which is the deferred **direction-reversal** gap
  (`type-system.md` §10 item 4), not the alternation gap.

  > **Unverified.** The original note claimed an "`optional<T>` sketch in the `Brainstorming Interfaces`
  > PDF was reaching for exactly this." Same as application 1 — neither the PDF nor an `optional<T>`
  > sketch could be located anywhere in the current tree. Flagged, not fixed.

  A partial escape: rules arriving as a `single(rule[n])` over an `infinite` lifetime through the
  sticky-latch path works, if you are willing to configure via the datapath rather than a side channel.

**On area**: no TCAM primitive, so ternary matching is built from LUTs — far less dense than real TCAM.
Practical rule counts will be tens, not thousands. Another concrete argument for a time-multiplexing
combinator.

### 6. Token bucket policer

**Policer vs. shaper is the decisive distinction.** A *shaper* buffers non-conforming packets and
releases them when tokens accumulate — that needs queueing and backpressure, so it is blocked. A
*policer* decides conforming/non-conforming and marks or drops, with no buffering. Feasible today.

**Refill every cycle; no timestamp.** Tracking `last_update` and computing `elapsed × rate` is the
*software* idiom, needed because software cannot wake every microsecond. Hardware runs every cycle for
free, so it just adds — no timestamp register, no multiplier, and the "add before deciding" ordering
question dissolves because the registered value is always current.

The fractional-rate problem is handled with a **fixed-point accumulator**: 1 Gbps at 200 MHz is 0.625
bytes/cycle, so keep tokens in units of 1/2^k bytes and add `round(0.625 × 2^k)` per cycle, with debits
shifted left by k. All integer, one adder.

(Timestamps *do* win for many buckets — you cannot add to 1000 buckets every cycle, so you lazily refill
the one you touch. That needs BRAM, so it is blocked anyway.)

**Packet size is not known until the last beat**, so conformance cannot be decided at the first beat.
The standard answer is **deficit accounting**: admit at the first beat if `tokens > 0`, debit actual
bytes as they flow, and let tokens go temporarily negative — which throttles the *next* packet.

```text
conform  = first_beat ? (tokens > 0) : held_conform      // latched for the packet
bytes    = last ? count_ones(keep) : 32
debit    = (fire && conform) ? (bytes << k) : 0
tokens'  = min(tokens + R, CAP) - debit
mark     = !conform                                       // out with the data
```

Debit only on conforming packets — that is srTCM's rule (RFC 2697); a red packet does not consume
budget. Because AXI-Stream `tkeep` is contiguous, `count_ones(keep)` is a leading-ones count (a small
priority encoder), and it only matters on the last beat.

**Mark, don't drop.** Beyond matching RFC 2697/2698 (green/yellow/red markings), dropping breaks the
current setup: `axis_mutual_exclusion` expects exactly one packet out per packet in, so a dropped packet
would desynchronize its FSM. Marking keeps the flow 1:1 and needs no wrapper changes.

Roughly 40 lines. The smallest possible demonstration that cross-lifecycle state works without a raw
`register`.

> **Two complications found 2026-10**, both relevant to that claim:
>
> - **It needs per-beat state that persists across packets** — tokens are debited as bytes flow and
>   carried between packets. That is the one near-path case needing a standing scan able to consume a
>   nested channel at beat granularity (or a flatten). Written instead as an outer scan over packets
>   with an inner scan over beats, the outer state would only round-trip the inner's and duplicate the
>   register bank. See `type-system.md` §10 item 17.
> - **The every-cycle refill is not expressible as a scan over beats at all.** A scan advances on
>   `fire`, and a cycle carrying no beat does not fire — but the refill must happen on every clock
>   regardless of data flow. That is state advancing on *cycles* rather than *beats*, which is arguably
>   a layer-3 concern, and the design does not yet address it. Unresolved.
>   *Plausible answer (2026-10):* a `standing_scan` over a zero-input, always-valid source counts every
>   cycle — but it must sit where nothing stalls (`@valid`, or its own island), since a `@ready_valid`
>   stall would freeze the timer. See "`@dataflow` is combinational" in `type-system.md` §6.

### 7. HyperLogLog

**What it does:** count *distinct* elements in a stream. Exact counting needs memory proportional to the
distinct count; HLL gets ~2% accuracy in a couple of kilobytes.

**Intuition:** hash each element uniformly. After *n* distinct items the most leading zeros you would
expect to see is about log₂(*n*) — a hash starting with 10 zeros suggests ~2¹⁰ distinct things. One
observation is extremely noisy, so split into many buckets and average.

**Algorithm:**

1. Hash the element → `h` (say 64 bits).
2. Top *p* bits are a bucket index *j* (so *m* = 2^*p* buckets).
3. In the remaining bits, count leading zeros, +1 → ρ.
4. `M[j] = max(M[j], ρ)`.
5. Estimate ≈ α_m · m² / Σⱼ 2^(−M[j]).

**Why it is CMS-shaped:** identical dataflow — *hash → index → update a small counter in an array → read
out an aggregate*. CMS uses *d* hashes and `+=`, taking the min at query time; HLL uses one hash and
`max`, taking a harmonic mean. Structurally HLL is *simpler*: one array instead of *d* rows, one update
per item instead of *d*.

That is why it is the right test of the `register` restriction. CMS uses `register(sketch4(16))`
directly at `netfpga/src/cms/processor.gapl:29`; if `scan` over an `infinite` stream cannot express the
same shape cleanly, that decision does not hold.

> **Reframed 2026-10.** CMS's initial version is **per-packet** — it treats each packet independently —
> so it is not in fact a cross-lifecycle example: it is `oneach` over packets with a `scan` over each
> packet's beats. That makes **HyperLogLog itself** the near-path test of cross-lifecycle state, and it
> passes cleanly: counting distinct flows wants one update per packet, so HLL is a standing scan over
> `infinite(packet)` whose step hashes the header — no inner per-beat scan, so no duplicated storage
> and no flatten needed. See `type-system.md` §6 "State scoping" and §10 item 17.

**What to build:** reuse `count_min_int_hash0` (hash functions already exist, and CMS already hashes a
whole 256-bit beat). Bucket index = top *p* bits; ρ = a leading-zero count, purely combinational. State
is `wire[6][m]` — 6 bits holds ρ up to 57 for a 64-bit hash.

**Export raw registers rather than computing the estimate.** The Σ2^(−M[j]) and division are the
expensive part, and CMS already takes exactly this approach — it flattens the sketch straight to
`o.data` and lets something off-chip interpret it.

**Pick *p* carefully.** Standard error ≈ 1.04/√m:

| *p* | buckets | state bits | error | mux |
|---|---|---|---|---|
| 6 | 64 | 384 | 13% | 64:1 |
| 8 | 256 | 1,536 | 6.5% | 256:1 |
| 12 | 4,096 | 24,576 | 1.6% | 4096:1 |

*p*=8 is the sweet spot in registers. *p*=12 is what production uses and needs BRAM — a natural on-ramp
to application 8. Note the **read-modify-write hazard** when you get there: consecutive items hitting the
same bucket need forwarding logic, and CMS has the identical problem the moment it moves to BRAM.

**One design choice:** CMS updates every beat. Counting distinct *flows* wants one update per *packet* —
hash the header once. That needs a "first beat" flag, free from layer 2 later, one register today.

---

## Tier 3 — blocked, and useful because of it

### 8. Bloom filter

*k* hashes, *k* bit-array lookups, AND them. Conceptually trivial, blocked entirely on the memory
primitive — a useful bit array does not fit in registers. **The cleanest possible forcing function for
BRAM**, with no other complications to confound the design work.

### 9. 2D stencil / line-buffered image filter (Sobel, box blur)

Needs a line buffer, so it needs memory. More interestingly it needs **lookahead across beats** —
output row *n* requires rows *n−1* and *n+1*. That is the causality condition identified as the boundary
of efficiency-neutrality (`type-system.md` §2), and it is the only application here that violates it. The
test of whether the layer-2 cost model holds up. Squarely Aetherling's territory, so there is prior art
to compare against.

### 10. Round-robin scheduler / arbiter over N input streams

Blocked on the highest-priority remaining gap after alternation: multiple `stream` inputs with no way to
declare their rate relationship. Also needs `align` rather than `zip` (inputs genuinely differ in length
and arrival), and it is where fork/join diamond hazards show up for real. `axis_arbiter` in
`brainstorming/archive/examples/checksum_gadl.txt` is a hand-written version to compare against.

---

## Cross-cutting constraints

**Area, because everything unrolls spatially.** MD5's 64 rounds fully unrolled is already large;
AES-128's 10 rounds likewise. Without a time-multiplexing combinator, fewer of these fit on a NetFPGA
than the hand-written equivalents, and the gap widens with algorithm depth. Worth measuring early on one
Tier 1 application to know the multiplier.

**Header rewriting, and the architecture that unblocks it.** The current NetFPGA wrapper assumes the
header is always two beats and leaves it untouched (padding the front of the body to make it so), which
forces the packet length to stay constant and the checksum to be omitted — see `type-system.md` §8. The
shape intended to replace it: **split the stream into a header stream and a body stream, transform each
independently, then concatenate and repack** to remove the dead space between the header's end and the
body's start. That is `packet_packer`'s architecture generalized, and it is blocked on the
rate-relationship gap (`type-system.md` §10 item 2) — a positional demux, a concatenation of two
independently-produced streams, and a gearbox are all instances of it. Worth noting the repacking is
*statically* determined whenever the header sizes are constants, so it is much easier than the general
dynamic-offset case.

**Checksums: computing the sum is easy, placing the field is not.** A one's-complement sum over a
stream is an ordinary `scan` accumulator, and the primitives already exist in the repo
(`util/ones_complement_sum.v`, `util/ones_complement_addition.v`, currently unreferenced — note the
review found the sum builds an N−1 deep serial adder chain where a balanced tree costs the same area).
The real difficulty is that a checksum over the whole message lives in a field at the *front* of it.
Split by which checksum:

- **IPv4 header checksum** — covers only the 20-byte header, so everything it needs is in the first two
  beats. No buffering. A modified field can also be patched incrementally: RFC 1624's
  `HC' = ~(~HC + ~m + m')` is two one's-complement adds, no re-sum.
- **UDP/TCP checksum** — covers a pseudo-header plus the entire payload, with the field at the front.
  Three cases:
  - *UDP over IPv4:* legally optional. RFC 768 defines a transmitted zero as "not computed," and
    receivers must accept it. Mandatory for UDP over IPv6, so this escape closes on a dual-stack design.
  - *Payload-reducing transforms (MD5):* no buffering needed. The output payload is a 128-bit digest
    you are holding before emission anyway, so it is checksummed then. The large input is never stored.
  - *1:1 transforms (AES-CTR):* genuinely requires store-and-forward, and therefore the memory
    primitive (application 8). Streaming both the plaintext and ciphertext sums with a `scan` and
    patching incrementally does **not** escape it — the header still cannot leave until the body's sum
    is known, so something has to hold the body. This is not an FPGA or GAPL limitation; it is why
    store-and-forward switches and NIC checksum offload exist.

The split/recombine architecture above is the right shape for this, since holding the header as its own
stream is what permits filling it in late. The buffering cost then reduces to "how large is the body I
must hold" — zero for reducing transforms, the whole packet for 1:1 ones.

> **Decision (2026-09): transmit a zero UDP checksum and ignore the problem.** Legal under RFC 768 for
> UDP over IPv4, which is the traffic these applications target. This is what keeps AES-CTR and other
> 1:1 transforms deployable without store-and-forward, so it removes checksums as a driver for the
> memory primitive (which is still needed for applications 8 and 9, and for realistic sketch sizes).
> **The escape closes** for UDP over IPv6, where the checksum is mandatory, and for TCP, which has no
> zero-means-absent encoding at all — so revisit if either enters scope.

**Flatly out of reach**, regardless of the gaps above: TCP flow reassembly, anything needing
out-of-order buffering, anything needing large dynamic memory allocation. ClickNP's IPSec gateway is
borderline — the crypto is fine (application 3), but the SA lookup table needs real memory. Its pFabric
scheduler is application 10 plus a priority structure, so it is two gaps deep.

---

## Appendix: a faster path to FSMs

The alternation gap (`type-system.md` §4) blocks the parser family. **Only the parser family** — CRC32,
AES-CTR, token bucket, HyperLogLog, and the static classifier need `scan`/`fold` and nothing else. So
the FSM gap blocks three applications, not ten.

Options for closing it, from the PL literature:

**A. Sum types + `match` over an explicit state — recommended.** The classic FSM encoding with the
functional refinement: state is a tagged union, transitions are `State × Input → State × Output`,
dispatch is an exhaustive `match`. That is a Mealy machine, which is what `scan` is.

*Today:* works immediately, with state in a `register`. The value-level half already exists —
`conditional(T)` and `if_else` are in the stdlib, and CMS uses `if_else(sketch4(16))` as a runtime mux.

*In the future GAPL:* the `register` becomes a `scan`, per-lifecycle reset becomes automatic, the parser
becomes a `@ready_valid` combinator, and the sum types and `match` carry over untouched. **Zero
throwaway** — variants are required for lifecycle alternation anyway, so this is a strict prefix.

*What you get:* type-safe parsed output, untyped lifecycle. `match` exhaustiveness stops you reading
`ipv4.src` off an IPv6 result; what layer 2 still does not know is that the beat structure varied. Maybe
80% of the safety benefit for well under half the work.

*Implementation:* represent a variant as **a record with a tag field plus one field per case** — no
overlay. The existing `WireVector`/`WireVectorGroup` flattening already handles nested records, and
`match` lowers to muxes selected by the tag. Overlaying payloads onto a shared wire budget is a pure
optimization for later. This makes option A mostly grammar + resolver work.

**B. Parser combinators — a library, once you have A.** `tag(v)`, `then(a, b)`, `choice(sel, branches)`,
`count(n, p)`, composed by ordinary function composition. Fits GAPL's identity well: composition of
parsers *is* structural composition of modules. And if each combinator carries its lifecycle type,
composing combinators composes lifecycle types — an incremental path into option C rather than a
big-bang change.

**C. Lifecycle alternation** — the full version. Implementation is not novel: Brzozowski derivatives give
a textbook regex→DFA construction.

**D. Coroutines / generators — rejected.** The modern PL answer (write sequential code with suspension
points, split at each `yield`), but GAPL bodies are dataflow graphs, not statement sequences.
Suspension points need a program-counter model, which means a second sub-language with different
semantics. Largest erosion of "the user decides the structure."

**E. Static recursive descent — usable today, ugly.** Given DAG-only parsing, the recursion depth is
statically known, and GAPL already does compile-time recursion (`md5_hash_block_from_iteration`). Combine
with `if_else` for the runtime selects and you can write a fixed-depth parser today with **no new
features at all**. Verbose, output is a struct with validity flags.

**Suggested sequencing:** sum types + `match` → fixed-protocol parser → parser combinators once `scan`
exists → lifecycle alternation when the combinators reveal what the type constructors need to be. Step 1
is the only item with the property that it is useful under *today's* GAPL, before any layer-2/3 work
lands.
