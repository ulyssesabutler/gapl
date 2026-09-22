# Header Parser Sketch

A paper design for a fixed-protocol header parser (Ethernet + IPv4/IPv6) on the NetFPGA pipeline,
written to test the layer-2 machinery — `seq`, views, spatial alternation, `scan`-state carry — the way
`md5-lifecycle-sketch.md` tested `stream`/`fold`. The point is to hand-typecheck a real pipeline and
find out what breaks. See `type-system.md` for the design this exercises, and `applications.md` #1 for
why this application was chosen.

Layer 3 is set aside throughout, per the scoping decision recorded in `type-system.md`.

Syntax follows `antlr/src/main/antlr/CST.g4` where it exists. Anything marked **NEW** is proposed and
does not exist yet — `view`, `datatype`, `select`, `oneach`.

## The target

Extract the 5-tuple (source/destination IP, source/destination port, IP protocol) at fixed byte
offsets, and pass the packet through unmodified. Deliberately fixed-protocol: IHL = 5, no VLAN tags, no
IPv6 extension headers. Lifting any of those makes the body's start offset a computed value, which is
the deferred dynamic-offset package (`type-system.md` §10 item 15).

Byte offsets, and where they land on a 32-byte beat:

| field | bytes | IPv4 | bytes | IPv6 |
|---|---|---|---|---|
| dst MAC | 0–5 | beat 0 | 0–5 | beat 0 |
| src MAC | 6–11 | beat 0 | 6–11 | beat 0 |
| ethertype | 12–13 | beat 0 | 12–13 | beat 0 |
| version / IHL | 14 | beat 0 | 14 | beat 0 |
| IP protocol / next header | 23 | beat 0 | 20 | beat 0 |
| src IP | 26–29 | beat 0 | 22–37 | **straddles 0/1** |
| dst IP | 30–33 | **straddles 0/1** | 38–53 | beat 1 |
| src port | 34–35 | beat 1 | 54–55 | beat 1 |
| dst port | 36–37 | beat 1 | 56–57 | beat 1 |

Two things fall out of this table immediately, and both shape everything below: **both protocols finish
their headers within beats 0–1**, and **fields straddle the beat boundary in both** (IPv4's destination
address splits 2/2, IPv6's source address splits 10/6).

## Layer 1: the beat and its views

```text
interface axi_beat { data: wire[256]; keep: boolean[32]; }
```

Slices follow the `[low:high]` convention used in `netfpga/src/md5/processor.gapl`; byte *b* of a beat
occupies `data[8b : 8b+7]`.

```text
view beat0 of axi_beat {                          // NEW
    eth: {
        dst_mac:   data[0:47];
        src_mac:   data[48:95];
        ethertype: data[96:111];
    };
    ip: {
        version:   data[112:115];
        ihl:       data[116:119];
        protocol:  data[184:191];                 // byte 23
        src_ip:    data[208:239];                 // bytes 26-29
        dst_ip_hi: data[240:255];                 // bytes 30-31 - the straddle
    };
}

view beat1_v4 of axi_beat {                       // NEW
    dst_ip_lo: data[0:15];                        // bytes 32-33
    src_port:  data[16:31];                       // bytes 34-35
    dst_port:  data[32:47];                       // bytes 36-37
}

view beat1_v6 of axi_beat {                       // NEW
    src_ip_lo: data[0:47];                        // bytes 32-37
    dst_ip:    data[48:175];                      // bytes 38-53
    src_port:  data[176:191];                     // bytes 54-55
    dst_port:  data[192:207];                     // bytes 56-57
}

view raw_beat of axi_beat { bytes: data[0:255]; } // NEW
```

All four are views of the same base, per the one-base-per-datatype rule. Note `beat0` is a **struct of
structs** — beat 0 genuinely contains the tail of one protocol header and the head of another, so a flat
view would misrepresent it. All four are **partial**: none names every bit, which is legal for reading.

## Layer 2: the datatype

```text
datatype packet seq {                             // NEW
    h0:   single(beat0);
    h1:   single(select(h0.eth.ethertype) {       // NEW - spatial, registered selector
              0x0800 => beat1_v4;
              0x86DD => beat1_v6;
              else   => raw_beat;
          });
    body: stream(raw_beat);
}
```

Note what `select` is doing here: it chooses an **interpretation of beat 1**, not a continuation with a
different beat count. Both branches are exactly one beat. See finding 1.

> **Superseded (2026-09).** A datatype never contains an expression, so `select(h0.eth.ethertype)`
> above is not how this is written. `choice` exists but carries **no selector**: the *producing*
> function decides which branch its output takes and publishes that as a tag on the channel, and
> consumers are bound by it. See "Alternation: the producer chooses" in `type-system.md` §4. So the
> IPv4/IPv6 distinction here would be declared as `choice { v4: …; v6: … }`, with the parser's own body
> doing the ethertype comparison. The sketch is left as written because findings 1–6 below were derived
> from it and remain valid — finding 1 in particular is what set this whole line of reasoning going.

## The pipeline

```text
function parse_application() i: infinite(stream(axi_beat))
                          => o: infinite(stream(axi_beat)),
                             keys: infinite(single(five_tuple))
{
    i => oneach(parse_packet) => o, keys;
}

function parse_packet() i: stream(axi_beat)
                     => o: stream(axi_beat), key: single(five_tuple)
{
    // The packet passes through unmodified; the key is accumulated as scan state.
    ...
}

interface five_tuple {
    src_ip:   wire[128];    // widest case (IPv6); IPv4 occupies the low 32
    dst_ip:   wire[128];
    src_port: wire[16];
    dst_port: wire[16];
    protocol: wire[8];
}
```

Cross-phase carry is `scan` state, per `type-system.md` §4 — during `h0` the step function writes
`protocol`, `src_ip` and the high half of `dst_ip` into the accumulator; during `h1` it writes the low
half and the ports. The accumulator type is written in the signature, so its cost (≈296 bits at the
IPv6 width, or 104 if IPv4-only) is visible in the source rather than inferred.

## Hand typecheck

Let `N` = beats in the packet.

| stage | type | beats |
|---|---|---|
| `i` | `stream(axi_beat)` | N |
| `h0` | `single(beat0)` | 1 |
| `h1` | `single(beat1_v4 \| beat1_v6)` | 1 |
| `body` | `stream(raw_beat)` | N − 2 |
| `o` | `stream(axi_beat)` | N |
| `key` | `single(five_tuple)` | 1 |

One lifecycle in, one lifecycle plus one key out. The `seq` ends when `body` ends, and `body`'s `last`
is the packet's `last` — one delimiter, matching `tlast`, per the one-variable-extent-per-channel rule.

The derived FSM is a 2-bit phase counter plus one registered bit for the resolved `select` branch. No
datapath. That is the entire layer-2 cost of this application.

---

## What the sketch found

### 1. Selector *provenance* and selector *target* are orthogonal — the spatial/temporal split was too coarse

`type-system.md` §4 defines spatial alternation as "selector and selected content in the same beat" and
temporal as "continuation differs in beat count." The IPv4-vs-IPv6 case is neither: the selector
(ethertype) is in beat 0, what it selects is the *interpretation* of beat 1, and the beat count does not
change at all.

The framing conflated two independent axes:

| | selector from the **current** beat | selector from an **earlier** beat |
|---|---|---|
| selects an **interpretation** (same beat count) | combinational mux, comparator on the critical path | combinational mux, registered selector — **this case** |
| selects a **continuation** (different beat count) | FSM transition, comparator on the transition path | FSM transition, registered selector |

All four cells are meaningful and the costs differ. The document should be restated on these axes
rather than on a two-way spatial/temporal split.

### 2. The natural syntax violates our own access rule — and the fix is principled

`select(h0.eth.ethertype)` in the `h1` phase reads a *previous* phase's view, and §4 says a phase's view
is live only during that phase. Taken literally, the parser cannot be written as declared; the ethertype
would have to be threaded through `scan` state by hand, which makes datatype definitions
non-self-contained — the format would no longer be describable in one place.

The resolution: **selectors in a datatype definition may reference earlier phases' fields, and the
compiler registers the result as part of the FSM state it already derives.** This is not an exception to
the access rule, it is §4's own "the state is the tag" principle — and crucially the compiler holds the
*resolved branch index*, not the raw selector field. For a two-way `select` that is **one bit**, not the
16 bits of ethertype. Cost is bounded by branch count, not by field width, which is what makes it
categorically different from the 296-bit cross-phase carry that motivated the restriction.

### 3. `stream` is one-or-more, but the body is routinely zero beats

A minimum-size Ethernet frame is 64 bytes — exactly two beats — and the headers alone account for 54 of
them. So `body: stream(raw_beat)` must accept **zero** beats, while §4 defines `stream(T)` as "one or
more beats, bounded."

This is not an edge case; minimum-size frames are the dominant case in switching benchmarks. Either
`stream` becomes zero-or-more, or `seq` needs an optional trailing phase. Worth noting the choice has a
downstream consequence: if `stream` is zero-or-more, then "this stream is non-empty" stops being a type
guarantee that a `fold` can rely on, which interacts with §4's rule that `fold` requires a bounded
stream.

### 4. The datatype asserts a minimum length the wire can violate

`seq { h0: single; h1: single; body: stream }` claims the packet is at least two beats. A runt frame
ends — `tlast` arrives — during `h0`. Nothing in the design says what happens.

This is a *different* failure from the `else` case settled in §4. There, no branch matched a selector;
here the lifecycle terminated before the type permitted it. The `else` machinery does not cover it, and
the same desynchronization argument applies: whatever the FSM does, it must return to a clean state for
the next packet or every subsequent lifecycle on the channel is corrupted. Real parsers need this; it
should be designed rather than discovered during bring-up.

### 5. Straddling fields must be hand-reassembled, which is what `axis_parser` exists to avoid

IPv4's destination address splits 2 bytes / 2 bytes across the beat boundary; IPv6's source address
splits 10 / 6. Under the view model, neither is nameable as a single field — `beat0` names `dst_ip_hi`,
`beat1_v4` names `dst_ip_lo`, and the `scan` step function reassembles them by hand.

That is precisely the work `netfpga/.../util/axis/axis_parser.v` automates: it takes `START_INDEX` and
`END_INDEX` *of the whole packet* and accumulates across beats internally, so a straddling field is
named once. So the two mechanisms are complementary rather than alternative — **views handle
within-beat fields well and straddling fields badly; the stdlib `parse(START, END)` is the reverse.**

The consequence for sequencing: `type-system.md` §6 defers the stdlib `parse` as "future work" for the
dynamic case, but this application needs it for the *static* case too, purely because protocol headers
do not align to bus widths. It should be pulled forward.

(This does not affect the pass-through path. A parser that only extracts fields never needs the payload
as a separate stream, so the fact that the body logically starts mid-beat-1 is invisible. It would bite
a stage that wants to operate on the payload, which is where normalization becomes unavoidable.)

### 6. The parser's natural signature has two outputs at different rates

`parse_packet` produces `o: stream(axi_beat)` (N beats) and `key: single(five_tuple)` (1 beat). The key
is not complete until beat 1, but the stream begins at beat 0 — so the two ports advance at genuinely
different rates, and there is no syntax for their relationship. That is §10 item 2, the deferred
rate-relationship gap, arriving in the *first* application rather than in the round-robin arbiter where
`applications.md` predicted it.

`applications.md` already anticipates the fix ("the packet must be delayed by as many beats as the key
spans — two beats of registers, 512 bits, nothing"), which works and is cheap. But the *type* still
cannot say "one key per packet, available from beat 2 onward," so the obligation stays unchecked. Worth
knowing that the deferral is less comfortable than it looked.

---

## What this application does *not* exercise

Worth recording, because it changes sequencing: **the fixed-protocol parser never needs temporal
`choice`.** Both IPv4 and IPv6 fixed headers complete within beats 0–1, so no branch changes the beat
count. It exercises `seq`, views, spatial alternation, and `scan`-state carry — and `choice`, which
absorbed most of the design effort in the 2026-09 session, is not on its critical path.

Temporal `choice` becomes necessary at the next step up: IHL > 5, VLAN stacking, IPv6 extension headers
— all of which are also exactly the cases that make the body offset a computed value and pull in the
dynamic-offset package. Those two features are therefore a single milestone, not two.

## Open questions this touches

- **Zero-length `stream`** (finding 3) — new; not currently in `type-system.md` §10.
- **Early `last` / runt frames** (finding 4) — new.
- **Restating alternation on two axes** (finding 1) — supersedes part of §4.
- **Selectors referencing earlier phases** (finding 2) — resolved here, needs recording in §4.
- **Pulling the stdlib `parse` forward** (finding 5) — changes §6's "future work" framing and
  §10 item 15's packaging.
- **Rate relationships** (finding 6) — §10 item 2, arriving earlier than expected.
