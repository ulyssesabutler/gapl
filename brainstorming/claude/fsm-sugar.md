# FSM Sugar — Design Notes

**Status:** design. Nothing in the "Proposed" sections is implemented. The "Verified today" sections
were established by compiling real GAPL against the tree at `172ae65` and reading the analyzer and
serializer source; each carries a `file:line`.

**Scope.** Syntactic sugar for writing finite state machines at the *value* level. This document
deliberately decides nothing about lifecycle types, protocols, views, or datatype compatibility —
those belong to the type-system track (`type-system.md`). Where the two meet, §9 flags the question
rather than answering it.

**Companion files.** `fsm-sugar-examples/` holds three FSMs written in today's GAPL and compiled to
Verilog with the `gapl` CLI. They are the evidence for §2 and §3 and are reproducible; see the README
there.

---

## 1. Verified facts about today's GAPL

These cost real investigation and several of them contradict what the surrounding design notes assume.
Do not re-derive.

### 1.1 `if` in a circuit body is compile-time elaboration, not a mux

`NodeBuilder.kt:236-247` evaluates a `conditional`'s predicate with
`StaticExpressionEvaluator.evaluateStaticExpressionWithContext` and then splices in *one* branch's
statements. The other branch generates no hardware. This is what `md5_iteration`'s
`if (iteration <= 15)` chains rely on.

**Consequence for this design: `if`/`else` are unavailable as FSM syntax.** They already mean
something else, and that meaning is load-bearing across the repo.

### 1.2 Every operator in `expression` is static-only. There is no `expression` → netlist lowering

`CST.g4:68-82` gives `expression` arithmetic, comparison, `&&`/`||`, and accessors. Every use of those
operators in the analyzer goes through `StaticExpressionEvaluator`, and there are exactly four call
sites — parameter values (`ParameterValue.kt:28`), interface sizes (`ProgramContext.kt:83`), static
`if` predicates (`NodeBuilder.kt:237`), and slice indices (`NodeBuilder.kt:554/558/578`). **No code
path anywhere turns an `expression` operator into a gate.**

Writing one in circuit position is rejected outright:

```text
function infix_test() a: w8, b: w8 => o: boolean {
    a == b => o;
}
```
```
error at 4:4-10: This looks like a value expression (an integer, boolean, or arithmetic/comparison
expression) - circuit expressions must reference a wire, an interface, or a function call instead
```

(`ResolverDiagnosticKind.kt:43`.) Runtime comparison must be spelled `equals(8)`, `less_than(24)`, and
so on, with both operands pre-declared as nodes.

> **This matters to the parallel track.** `type-system.md` §4 decides "a selector is an `expression`,
> not a `circuitExpression`… with zero grammar delta and combinational-by-construction for free." The
> grammar half is right. The "for free" half is not: a `choice`/`select` selector like
> `ethertype == 0x0800` compares a *runtime* field against a literal, and the machinery to lower that
> comparison to hardware **does not exist in any form today**. It is the same missing machinery this
> document needs in §6. Flagged, not decided — see §9.

### 1.3 The record/vector transformer syntax parses but is unimplemented

`CST.g4:84-90` defines `transformer` and `recordTransformerEntry`, and `circuitExpressionType` admits
`transformer transformerType=(In|Out|Inout) interfaceType=expression`. The resolver rejects all of it:

```text
declare conds: [
    0: c, a
] out conditional(w8)[1];
```
```
error at 4:4-6:28: Transformers are not supported yet
```

(`CircuitExpressionScope.kt:83`, `ResolverDiagnosticKind.kt:33`.) **No `.gapl` file in the repo uses
the syntax**, and there are no tests for it.

So the framing that today's FSMs are "hand-rolled using the record-transformer syntax" describes the
*aspirational* syntax in `brainstorming/archive/examples/checksum_gadl.txt`, not the language. Real
code assigns into a `conditional(T)[n]` vector one index at a time
(`verilator-test/tests/priority-router/test.gapl`).

Also note the grammar detail: transformer entries have **no terminator**. `0: c, a;` is a parse error;
entries are juxtaposed.

### 1.4 `priority` already lowers to exactly the Verilog hand-written FSMs contain

`priority(T, n)` takes `conditionals: conditional(T)[n], default: T => o: T` and emits:

```verilog
always @(*) begin
    if (node7$conditionals$condition$input[0:0]) begin
        node7$output$output_register = node7$conditionals$value$input[1:0];
    end else if (node7$conditionals$condition$input[1:1]) begin
        ...
    end else begin
        node7$output$output_register = node7$default$input;
    end
end
```

Compare `axis_mutual_exclusion.v:103-112`, which is character-for-character the same shape. **The
primitive is already correct. What is missing is only a way to write it down.**

### 1.5 `mux` emits a `case` with no `default`, so `n < 2^selector_width` infers a latch

`CaseStatement` (`compiler/.../verilogir/module/statement/always/AlwaysStatement.kt:59-76`) has no
notion of a default entry. A `mux(beat, 3, 2)` therefore emits `case (sel) 0: … 1: … 2: … endcase`
inside `always @(*)` driving a `reg`, with selector value `3` leaving that `reg` unassigned — latch
inference in synthesis, stale value in simulation.

Unreachable in today's uses, but **`match` over any tag type whose value count is not a power of two
lands on it directly**, so it has to be fixed as part of this work. §7 says how.

### 1.6 Output ports are write-only

`ingress_out.valid` as a *source* fails with `Bit width mismatch … of (nothing) (width of 0)`. Verilog
routinely reads back an `output reg`; GAPL cannot, so any "compute it, drive the port, and also branch
on it" pattern needs an explicit intermediate `declare`. This shows up in every FSM that gates its
next state on what it just emitted.

### 1.7 The runtime-selection vocabulary, in full

`if_else(T)` (stdlib, a 2-way `mux`), `mux(T, n, w)`, `demux(T, n, w)`, `priority(T, n)`. That is all
of it. `conditional(T)`, `valid(T)`, `last(T)`, `pair(T, U)` are the stdlib record interfaces
(`StandardLibrary.kt:34-58`).

---

## 2. The empirical baseline

The brief asked for the tedium to be measured rather than assumed. Three FSMs were written in today's
GAPL and compiled to Verilog; all three compile with **no new features**.

| FSM | source | code lines | `declare`s | `if_else` | `mux` | `priority` |
|---|---|---|---|---|---|---|
| Token bucket policer (`applications.md` #6) | `fsm-sugar-examples/policer.gapl` | 52 | 20 | 6 | 0 | 0 |
| 3-to-1 round-robin arbiter | `fsm-sugar-examples/arbiter.gapl` | 24 | 10 | 2 | 1 | 0 |
| `axis_mutual_exclusion` (3 named states) | `fsm-sugar-examples/mutex.gapl` | 41 | 14 | 2 | 0 | 1 |

But the decisive specimen is not a reconstruction. **`netfpga/src/md5-stream/processor.gapl` is 716
lines of real GAPL the author wrote, and it is by a wide margin the repo's densest FSM**: 32
runtime-select instantiations, 118 `declare`s, and seven state registers (`:61-67`).

Its two hot spots are the whole problem in miniature.

**Hot spot A — one 3-way priority chain, written ten times.** Lines 130-153. The author's own comment
says what the code cannot: `/* Merge the three hash-invoking cases (followup > terminal >
normal_complete/idle default). */`. Underneath, that single three-arm decision is spelled as **ten
`if_else` calls and seven intermediate `_t_or_n` names** because it has to be applied to five targets
independently:

```gapl
is_terminal, msg_block_terminal, msg_block_normal => if_else(wire[512]) => declare msg_block_t_or_n: wire[512];
pending_final_reg, literal(512, 0), msg_block_t_or_n => if_else(wire[512]) => declare msg_block_active: wire[512];

literal(7, 64) => declare valid_count_normal: wire[7];
is_terminal, block_valid_count, valid_count_normal => if_else(wire[7]) => declare valid_count_t_or_n: wire[7];
literal(7, 0) => declare valid_count_followup: wire[7];
pending_final_reg, valid_count_followup, valid_count_t_or_n => if_else(wire[7]) => declare valid_count_active: wire[7];

is_terminal, place_marker_terminal, false_b => if_else(boolean) => declare place_marker_t_or_n: boolean;
pending_final_reg, pending_final_marker_reg, place_marker_t_or_n => if_else(boolean) => declare place_marker_active: boolean;
/* … and the same two-line pattern again for needs_length_active and length_bits_active … */
```

**Hot spot B — seven register updates, each its own chain.** Lines 173-215, ten more `if_else` calls.
The guards repeat across registers: `is_terminal_overflow` appears in three separate chains,
`is_normal_start` in two. Nothing in the source groups "what happens when `is_terminal_overflow`".

---

## 3. Where the tedium actually is

Four distinct problems. Only two of them are about state machines, and they are not the two you would
guess.

### 3.1 Transposition — the real one

Verilog writes `always @(*) begin <defaults>; if (c) begin <several updates>; end end`: **the
condition once, N updates under it.** GAPL requires one `if_else`/`priority` per updated value, so a
condition used by N targets is written N times and the case structure exists only in the reader's
head.

Cost scaling is the point. Today, adding a case to an M-arm, N-target decision costs **N new
`if_else` instantiations and N new intermediate names**. It should cost one arm.

This is the problem the sugar must solve, and it is untouched by anything in the type-system track.

### 3.2 No runtime condition sublanguage — the biggest measurable win

`tokens > 0` must be written as:

```gapl
literal(24, 0) => declare zero_tokens: tokens;
zero_tokens, tokens_reg => less_than(24) => declare has_tokens: boolean;
```

Two lines, one throwaway name, and operand order that reads backwards from the predicate it encodes.
Every guard in every FSM pays this. In the policer, 4 of 20 `declare`s exist only to hold a literal;
in `md5-stream`, constants like `false_b`, `true_b`, `sixty_four7`, `fifty_five7`, `thirty_two7`,
`two_five_six_64` are all this tax.

**Measured across the corpus this is a larger ergonomic win than the `match`/`priority` sugar itself**,
and it is a prerequisite for the sugar being worth much — an arm whose guard is a bare node reference
just moves the two lines elsewhere.

### 3.3 No default value for a record

"Everything zero unless" needs a hand-written constructor, one line per field:

```gapl
function zero_beat() null => o: beat {
    literal(256, 0) => o.data;
    literal(32, 0)  => o.keep;
    literal_bit(0)  => o.valid;
    literal_bit(0)  => o.last;
}
```

GAPL cannot express this generically — there is no way to iterate an arbitrary interface's fields — so
it must be a compiler-provided primitive or it stays hand-written per record.

### 3.4 Position and phase tracking — smaller than expected

In the policer, `in_packet_reg` plus `first_beat` is **4 of 52 code lines**. The type-system track's
compiler-derived `first`/`last`/`phase` deletes exactly that.

So the brief's hypothesis is half right. Derived position is real but small — call it under 10% of the
policer — and it does nothing for §3.1 or §3.2.

> **The conclusion worth carrying:** *lifecycle types do not fix FSM tedium.* What remains after
> `scan` and derived position land is a **value-level expression and selection problem**, not a
> temporal one. That is why this work is genuinely separable from the type-system track, and why it is
> useful to do first.

---

## 4. Two constructs, and why they must stay apart

The brief anticipated this and the corpus confirms it. `axis_mutual_exclusion.v` contains both, in the
same 80 lines:

| | its next state (`:103-112`) | its outputs (`:52-97`) |
|---|---|---|
| shape | ordered guards over arbitrary booleans, mixing state tests and input tests | dispatch on the state tag alone |
| exhaustive? | no — needs a fall-through | yes — one arm per state |
| Verilog | `if / else if / else` | `if (state == X)` per output block |
| GAPL primitive | `priority(T, n)` | `mux(T, n, w)` |
| hardware | linear chain, depth ∝ arms | `case`, one selector |

They are not variants of one feature. They differ in what can be checked (exhaustiveness) and in what
gets built (chain vs. `case`). Collapsing them into one construct that guesses which to emit would
violate goal 7 — the user should pick the hardware by picking the keyword.

Both must fix transposition, so **both need arms that bind several targets at once**. That shared arm
shape is the thing to design once.

---

## 5. Proposed: `priority { }` — guarded multi-target chains

*Proposed, unbuilt.*

Sugar for `priority(T, n)`, keeping the name so the generated hardware is named in the source.

```gapl
declare next_state: phase;

priority {
    egress_done  { next_state: st_resetting;  }
    ingress_done { next_state: st_egressing;  }
    is_resetting { next_state: st_ingressing; }
    else         { next_state: state;         }
}
```

### Rules

- **The `else` arm is mandatory and defines the target set.** It must bind every target the block
  drives. Earlier arms may bind a subset; an unbound target takes the `else` value on that arm. This
  is Verilog's `defaults; if (c) override` idiom with the footgun removed — the footgun is exactly
  "forgot to write the default", and here it is a compile error.
- **Desugaring is per target.** For each target `t`, emit one `priority(type(t), k)` whose `k` arms
  are the arms that bind `t`, in source order, with the `else` binding as `default`. The compiler
  performs the transposition the user currently performs by hand.
- **Arms are ordered.** First match wins, exactly as `priority` already lowers.
- **Arms do not gate instantiation.** Every arm's hardware exists simultaneously; the arms select
  among results, they do not choose what to build. This is the single most important thing to state
  loudly, because it is the first thing a software-trained reader gets wrong. `if` (§1.1) *does* gate
  instantiation, and the two constructs sitting side by side in one language makes the distinction
  worth a diagnostic of its own.
- **Arms may contain ordinary circuit statements**, including `declare`, before their bindings.
  Subject to the rule above: those are built unconditionally.
- **A target may be a register input, a declared node, or a function output port.** `next_state: state`
  where `state` is a `register(phase)` reads the register and drives it — GAPL's existing convention,
  and how "hold" is written.

### Applied to hot spot A

The 16 lines / 10 `if_else` / 7 throwaway names above become one block whose columns line up as a
transition table, which is how FSMs are documented anyway:

```gapl
declare msg_block_active:    wire[512];
declare valid_count_active:  wire[7];
declare place_marker_active: boolean;
declare needs_length_active: boolean;
declare length_bits_active:  wire[64];

priority {
    pending_final_reg {
        msg_block_active: 0;                     valid_count_active: 0;
        place_marker_active: pending_final_marker_reg;
        needs_length_active: true;               length_bits_active: length_bits_reg;
    }
    is_terminal {
        msg_block_active: msg_block_terminal;    valid_count_active: block_valid_count;
        place_marker_active: place_marker_terminal;
        needs_length_active: needs_length_terminal; length_bits_active: length_bits_terminal;
    }
    else {
        msg_block_active: msg_block_normal;      valid_count_active: 64;
        place_marker_active: false;
        needs_length_active: false;              length_bits_active: 0;
    }
}
```

**Be honest about what this buys.** Raw line count is roughly a wash. What changes:

- the three-case structure is **stated** rather than reconstructible only from the author's comment;
- seven `_t_or_n` names and two `literal(…) => declare` lines disappear;
- priority order is written down instead of encoded in `if_else` nesting order;
- adding a fourth case costs **one arm** instead of five more `if_else` calls.

Across `md5-stream`'s two hot spots the sugar removes 21 `if_else` instantiations and roughly a dozen
single-use names, at neutral line count, and changes the cost of a new case from O(targets) to O(1).

### Naming

`priority` is currently an ordinary identifier used as a function call in
`{verilator-test,sim-test}/tests/priority-router/test.gapl`. Making it a keyword breaks that unless
`atom` is widened to `atom: (identifier=Id | Priority) parameterValues?`. That is a three-token
grammar change and is worth it: the sugar and the function are the same hardware and should have the
same name. `when` is free (it appears only in comments) if that judgement goes the other way.

---

## 6. Proposed: a runtime condition sublanguage

*Proposed, unbuilt. Per §3.2 this is the largest single ergonomic win, and §5 is much weaker without
it.*

Lower `expression`'s existing operators to hardware when their operands are circuit nodes:

```gapl
priority {
    egress_sel.valid && egress_sel.last   { state: phase.resetting;  }
    ingress_sel.valid && ingress_sel.last { state: phase.egressing;  }
    state == phase.resetting              { state: phase.ingressing; }
    else                                  { state: state;            }
}
```

**The static/dynamic rule is total and needs no annotation:** evaluate with
`StaticExpressionEvaluator` if every operand is static; emit a gate if any operand is a circuit node.
There is no ambiguous case, and a guard over static parameters constant-folds to nothing, so the two
readings never disagree about cost.

Mapping is mechanical and every target already exists: `==`→`equals(n)`, `!=`→`not_equals(n)`,
`<`/`>`/`<=`/`>=`→the four comparison functions, `&&`/`||`→`and()`/`or()`, `+`/`-`/`*`→`add`/
`subtract`/`multiply`, `true`/`false`→`literal_bit(1|0)`, and an integer literal in a typed position
→`literal(width, v)` with the width taken from the context.

Two consequences worth stating:

- **Integer literals become usable in binding position.** `valid_count_active: 64` replaces a
  `literal(7, 64) => declare …` pair. This alone accounts for a large share of §3.2's tax.
- **`!` has no token.** `CST.g4` has `!=` but no unary `Not`. `!i.last` needs one added, or
  `not()` stays explicit.

**Scope discipline.** This should be available in *guard and binding position*, not as a general
replacement for the `=>` datapath language. GAPL's identity is that the user writes the structure;
allowing arbitrarily deep expression trees to conjure datapath would erode that. A depth limit is
worth considering — the natural one being what the parallel track needs for selectors, which is
shallow by construction (§9).

---

## 7. Proposed: `enum` + `match { }` — exhaustive tag dispatch

*Proposed, unbuilt, and the piece with a real dependency on the type-system track.*

`match` is exhaustive dispatch on a tag, lowering to one `mux` per target. Exhaustiveness requires a
type whose values are enumerable, and **GAPL has no such type** — today a state variable is
`wire[2]` and nothing distinguishes it from a 2-bit number. So `match` implies an enum:

```gapl
enum phase { ingressing; egressing; resetting; }
```

- Width is `ceil(log2(n))`; values are written `phase.ingressing`.
- Nominal, like everything else the type-system track settled: two enums with the same arity are
  distinct types.
- `match` over it is exhaustive **by the type**, so no `else` arm is required — and an `else` on a
  total match should be an error, not dead code.

```gapl
match state {
    phase.ingressing { ingress_sel: ingress_in;  egress_sel: egress_in;  module_reset: false; }
    phase.egressing  { ingress_sel: zeros(beat); egress_sel: egress_in;  module_reset: false; }
    phase.resetting  { ingress_sel: zeros(beat); egress_sel: zeros(beat); module_reset: true; }
}
```

Arm bodies are the same shape as §5's, deliberately.

### The unhandled-encoding problem, and where its answer already is

Per §1.5, `mux` emits no `default`, so a 3-value enum in 2 bits leaves encoding `3` driving a latch.
`type-system.md` §4 has already settled this exact question for `choice`, and **the same rule should
apply unchanged**:

> Don't-care means "synthesis may route this to any existing branch," not "undefined state." …
> `simengine` asserts on the don't-care case.

Concretely: emit `default:` on the `case` pointing at an arbitrary existing arm (free, and keeps the
FSM out of an unreachable state), and assert in `simengine`. Reusing the decision rather than making a
second one is the point — this is one mechanism appearing at two levels.

### What enum-only `match` does and does not buy

It gives exhaustive *dispatch* and a tag type that cannot be confused with an integer. It does **not**
give the payload safety the appendix of `applications.md` describes — "`match` exhaustiveness stops
you reading `ipv4.src` off an IPv6 result" needs variants that *carry* payloads, i.e. sum types.

An enum is the zero-payload special case of a sum type, so this is a strict prefix, not a detour. But
payload-carrying variants overlap the type-system track's spatial alternation, which §4 there has
already decided must be a language construct (because a stdlib function cannot supply a static
guarantee). **That overlap is real and belongs to them; this document stops at enums.**

### A third shape, and why it is sugar rather than a construct

Lifecycle `select` in the parallel track is neither of §4's two shapes — it is a scrutinee matched
against *literals* with a mandatory `else`:

```text
select(h0.eth.ethertype) { 0x0800 => beat1_v4; 0x86DD => beat1_v6; else => … }
```

At the value level that is exactly `priority` with equality guards:

```gapl
match_value ethertype {         // sugar
    0x0800 { ip_view: v4; }
    0x86DD { ip_view: v6; }
    else   { ip_view: unknown; }
}
```
≡
```gapl
priority {
    ethertype == 0x0800 { ip_view: v4; }
    ethertype == 0x86DD { ip_view: v6; }
    else                { ip_view: unknown; }
}
```

So the family is **three levels of checking over one arm syntax**: tag `match` (exhaustive by type,
one `mux`), literal `match` (total by mandatory `else`, comparators + chain or a `case`), and
`priority` (arbitrary guards, mandatory `else`, chain). Recommend building `priority` first, tag
`match` second, and treating the literal form as sugar that may not need its own construct at all.

---

## 8. Grammar delta

Against `antlr/src/main/antlr/CST.g4`. Small, and the arm body is an existing rule.

```antlr
// tokens
Match:    'match';
Priority: 'priority';        // see §5 — widen `atom` to keep priority(...) callable
Enum:     'enum';
Not:      '!';               // §6; CST.g4 has != but no unary not

atom: (identifier=Id | Priority) parameterValues?;

// arm bodies are recordTransformerEntry plus a terminator — see the note below
armEntry: portIdentifier=Id Colon circuitExpression SemiColon;
armBody:  CurlyL armEntry* CurlyR;

priorityStatement: Priority CurlyL priorityArm* elseArm CurlyR;
priorityArm:       guard=expression armBody;
elseArm:           Else armBody;

matchStatement:    Match scrutinee=expression CurlyL matchArm+ CurlyR;
matchArm:          pattern (Comma pattern)* armBody;
pattern:           expression;

enumDefinition:    Enum declaredIdentifier=Id CurlyL (Id SemiColon)+ CurlyR;

circuitStatement:
      conditional                  #conditionalCircuitStatement
    | circuitExpression SemiColon   #nonConditionalCircuitStatement
    | priorityStatement             #priorityCircuitStatement    // new
    | matchStatement                #matchCircuitStatement       // new
;

program: (interfaceDefinition | functionDefinition | enumDefinition)+;
```

**`armEntry` is `recordTransformerEntry` plus a terminator.** That is not a coincidence worth glossing
over: a transformer `{ field: expr }` and a match arm `{ target: expr; }` are the same operation —
bind several named things in one construct. So **implementing the transformer (§1.3) and implementing
arm bodies are largely the same work**, which is why §10 recommends doing the transformer first.

One grammar wrinkle to settle while doing it. `recordTransformerEntry` (`CST.g4:84`) is
`portIdentifier=Id Colon circuitExpression` with **no terminator** — the trailing `;` in the grammar
file is ANTLR's rule delimiter, not a token in the entry. That was confirmed the hard way: `0: c, a;`
inside a vector transformer is a parse error (`extraneous input ';'`). Since a `circuitExpression` can
itself be a comma-separated group, juxtaposed entries are at best hard to read. Recommend **adding
`SemiColon` to `recordTransformerEntry`** rather than defining a separate `armEntry` — nothing uses
the rule today (§1.3), so it is a free change, and it keeps the two constructs literally identical.

---

## 9. Where this meets the lifecycle track — flagged, not decided

Three contact points. All three are questions for the type-system session.

1. **The selector-lowering gap (§1.2) is theirs too, and it is bigger than their notes assume.**
   `type-system.md` §4's "selectors are `expression`s… combinational-by-construction for free" is
   true about the grammar and false about the compiler: nothing lowers an `expression` operator to a
   gate today. §6 here proposes building exactly that machinery. **If both tracks need it, it should be
   built once, and whoever builds it should know the other is waiting on it.** No opinion offered on
   whether the lifecycle side wants the same static/dynamic rule.

2. **Whether value-level `match` and lifecycle `select` share a construct or only a syntax.** §7
   argues they share the *arm* shape and that lifecycle `select` is the literal-pattern member of one
   family. Whether the datatype-level construct should literally reuse `matchStatement`, or merely
   look like it, depends on decisions this document does not own — in particular whether a datatype
   definition may reference a scrutinee at all in the way a function body can. Flagged.

3. **`zeros(T)` versus don't-care.** §3.3 wants a way to write a record's zero value. `type-system.md`
   §3 is explicit that "unnamed bits are don't-care, not zero," because zero-filling silently asserts
   a value someone may come to depend on — while `NodeBuilder.validateWiresConnected`
   (`:121-147`) makes an undriven bit a hard error, so *something* must be written. A `zeros(T)`
   primitive is the obvious answer for the FSM case and is in tension with that principle. **Not
   decided here**; noted because §5's `else` arm makes it much less pressing (you write the default
   once per block rather than once per field), so the FSM work can proceed without it.

Nothing in §5, §6, or §7 depends on lifecycle types, protocols, or `scan`. The register-plus-feedback
idiom (`declare x: register(T); … => x;`) is left exactly as it is, since the parallel track's `scan`
is what replaces it.

---

## 10. Recommended sequencing

A ladder, ordered so each rung is independently useful and none is thrown away.

| rung | what | needs | buys |
|---|---|---|---|
| 0 | **Implement the existing transformer** (§1.3) | no grammar change at all | populating `conditional(T)[n]` in one construct instead of 2n indexed assignments; and it is most of the work for arm bodies (§8) |
| 1 | **Runtime `expression` lowering** (§6) | `!` token; an `expression`→netlist pass | the largest measured ergonomic win (§3.2); kills the literal-declare tax; **unblocks the parallel track's selectors** |
| 2 | **`priority { }`** (§5) | rungs 0–1; widen `atom` | fixes transposition; O(1) instead of O(targets) per new case |
| 3 | **`enum` + `match { }`** (§7) | rung 2; fix `mux`'s missing `default` (§1.5) | exhaustive dispatch; a tag type distinguishable from an integer |
| 4 | *(parallel track)* payload-carrying variants, lifecycle `select` | — | payload safety; spatial/temporal alternation |

Rung 0 is worth doing on its own account regardless of the rest — it is finishing something already
in the grammar, and it is the only rung with no design risk.

The `md5-stream` FSM is the right regression target throughout: it is real, it is the densest
specimen available, and §2 gives concrete before-numbers to measure against.

---

## 11. Rejected alternatives

| considered | rejected because |
|---|---|
| Reusing `if`/`else` for runtime selection | Taken, and load-bearing: `if` is compile-time elaboration (§1.1) and `md5_iteration`'s 64-way static chains depend on it. Two meanings for one keyword, distinguished only by whether an operand happens to be static, is precisely the silent-cost failure goal 7 forbids. |
| `=>` as the arm separator (`guard => { … }`) | `=>` means dataflow in every other position. A guard does not *drive* its arm's values; it selects among them. Juxtaposition (`guard { … }`) is unambiguous and costs nothing. |
| One construct that emits a `case` or a chain depending on the arms | The user would stop being able to tell which hardware they get — goal 7. Two keywords, two costs, stated in §4's table. |
| `default` arm written first, Verilog-style | In a priority chain the default *is* the fall-through, and writing it last matches both `priority`'s lowering and `if_else`'s operand order. Writing it first would read as an assignment that later arms mutate, which is the imperative reading this language does not have. |
| Arms that bind only a subset, with no mandatory `else` | That is Verilog's latch-inference bug. Making the `else` arm total and letting it define the target set keeps the convenience and removes the failure. |
| Sugar for the register-feedback idiom itself | `declare x: register(T); … => x;` is already terse, and the parallel track's `scan` is what should replace it. Out of scope by construction. |
| Extending `if_else` to n-ary instead of new syntax | Does not touch transposition (§3.1), which is the actual problem. An n-ary `if_else` still applies to one target at a time. |
