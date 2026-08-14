# Per-port hierarchical retiming: design plan

## Status: implemented and clearing CMS

Landed as `--retiming-solver per-port-hierarchical-minimal-register`, alongside (not replacing) the
existing hierarchical solver, whose output is byte-identical to before.

One deviation from the plan below: the per-port path uses **no super-nodes at all**. Root port
alignment is a direct equality constraint between the root's own port nodes, which keeps
`VirtualIONode` out of the new path entirely - so the design's step 7 (make the boundary structural
rather than a type check) came for free, and root alignment is unit-testable.

Results, all at the same `default: 1` delay model:

| case | old hierarchical | per-port |
|---|---|---|
| toy loop repro, periods 1-3 | infeasible | matches monolithic exactly |
| false-loop DAG (incl. `min`) | `Graph cannot contain zero-weight cycles` | compiles |
| md5 / aes / dag-retiming | works | works |
| **CMS at period 20** | **infeasible at every period** | **compiles at clock period 20** (cost below) |

### What per-port actually costs, and how the cost model got fixed on the way

An early version of this section reported "2946 registers flattened against 5102 monolithic" and
wondered why a restriction of the monolithic problem appeared to beat it. It never did. That number
was **register nodes**, which was exactly what the ILP minimised, and register-node count is not even
a property of the circuit - it depends on how the netlist happens to be chopped into nodes, and the
flat and contracted graphs chop it very differently. Flip-flops are the only like-for-like metric.

Counting those exposed two real defects in the cost model, both since fixed (see
`brainstorming/todo.md` > `## Retiming`):

1. **The objective counted edges, not bits** - a register on a 512-bit bus was priced the same as one
   on a 1-bit wire.
2. **Fanout was not shared** - `addWeightedConnection` built a chain per edge, so a bit driving three
   consumers at the same depth emitted three chains.

Flip-flop counts on CMS at period 20, `default: 1`, elaborating each module by its real instantiation
count:

| | before the fix | after |
|---|---|---|
| monolithic (`--flatten all`, `minimal-register`) | 5102 regs / 103,088 FFs | 1343 regs / **47,331 FFs** |
| per-port hierarchical (`--flatten recursive`) | 2947 regs / 1,303,996 FFs | 267 regs / **62,682 FFs** |

So per-port went from 12.6x worse than monolithic to **1.32x worse**, which is the ordering theory
predicts: hierarchical retiming is a restriction of the monolithic problem, so it should cost a little
more, and "a lot less" was the tell that the metric was wrong.

The remaining 1.32x is **granularity, not pricing**, and it is the "per port, not per field"
limitation listed under Risks below showing up on a real design. Under `--flatten all` MD5's 64
iterations are inlined, the 512-bit message block appears as separate 32-bit word nodes, and retiming
delays each word only as far as the iteration that consumes it. Under `--flatten recursive` each
`md5_iteration` is a module whose `m` port is the whole 512-bit vector, so the only edge available to
hold a register carries all 512 bits. Sharing brought that module from 626 x 512-bit registers down to
25 x 512, but per-*field* boundaries (`md5_input_block` is `word[16]`) are what would close the rest.

**Historical note on the FPGA run:** the 1.3M-flip-flop pre-fix build synthesised and ran correctly on
the NetFPGA (xc7vx690t, 866,400 flip-flops), which it should not have fitted. Vivado presumably
absorbed the long uniform delay chains via SRL inference. Not worth chasing now that the design is
62,682 flip-flops, but it is a reminder that flip-flop count is itself only a proxy for FPGA
resources - deep uniform delay lines are sublinear in depth, and LUTs usually bind before FFs do.

### Functional validation

New variations `netfpga/src/{aes,md5,cms}/per-port-min-register-count/` (same clock periods and delay
models as each app's existing retimed variation). Results:

| harness | aes | md5 | cms |
|---|---|---|---|
| `:netfpga:runSimKernelTest` (simengine) | pass | pass | pass |
| `:netfpga:runKernelTest` (Verilator, on the retimed Verilog) | **pass** | **pass** | **pass** |

The Verilator row is the one that means something, and two negative controls establish that it does:
supplying the wrong number of expected packets fails on packet count, and supplying the right number
with one corrupted nibble fails on packet contents ("mismatch at packet 0, output 0"). So a pass is a
byte-for-byte comparison of real packet output, not a vacuous green.

This is the first real evidence that retiming preserves behaviour on CMS's sketch feedback loop -
the case where the known "retiming does not recompute register reset values" gap
(`verilator-test/tests/cyclic-retiming` is marked deprecated for exactly this) would have been most
likely to bite. Evidence, not proof: the vectors are three short packets.

**`runSimKernelTest` cannot validate retiming at all**, and should not be read as if it did. It is
invoked with `-f <processor.gapl>` and runs the *untransformed* netlist through simengine, never
touching the compiler or the generated Verilog - so it produces identical results for every variation
of a given application, retimed or not. Useful as a source-level check; useless as a retiming check.

### Two bugs found and fixed on the way

- **Model construction must be deterministic.** The converter stored nodes in `IdentityHashMap`s and
  then iterated them to build the node list, so `graph.nodes` order - and therefore CP-SAT's variable
  order and `MinimalRegisterSolver`'s anchor-node choice - depended on JVM identity hash codes, i.e.
  on unrelated earlier allocations. This surfaced as **compilation output depending on `--log-level`**:
  at INFO and above, `Retimer.recordCircuitStats` runs a throwaway `Flattener(ALL).transform(...)`
  inside a `Logger.ifInfo`, which advances `AnonymousIdentifierGenerator`'s global counter and shifts
  every later allocation. CMS compiled at INFO and failed at WARN. Netlist `Node` has no
  `equals`/`hashCode` override, so a plain `LinkedHashMap` is *already* identity-keyed - it gives
  identity semantics and insertion order together, and is what these maps should have been.
  Worth knowing generally: `Logger.ifInfo` blocks in this codebase are not always side-effect free.
- **Arbitrary lags must not be propagated.** Ports in different weakly-connected components of a
  module have no chain of edges relating their lags, so the ILP's choice of relative lag between them
  is arbitrary; forcing a caller to reproduce it invents a requirement the hardware does not have.
  `PortBoundarySummary.portComponents` now scopes the port-lag constraints to within a component.


Plan for replacing hierarchical retiming's per-*module* boundary summary with a per-*port* one. This
doc is the design; `brainstorming/todo.md` > `## Retiming` is the backing detail - the measurements,
the reproductions, and the two failure modes that motivate it.

The short version: a module currently tells its parent three numbers (`delta`, `inputDelay`,
`outputDelay`) plus one combinational delay, each a maximum taken over *all* of its ports. That
merge is what makes CMS infeasible under hierarchical retiming at every clock period while
retiming fine monolithically, and it is also what turns some legal acyclic designs into false
combinational loops. The fix is to make every one of those quantities per-port (or per-port-pair),
and to let a module's ports sit at different lags.

## The problem

Today `HierarchicalNetlistLeisersonCircuitConverter.fromModule` gives every module a super-source
`s_i` and super-sink `s_o`, and `MinimalRegisterSolver` pins every input port to `r(s_i)` and every
output port to `r(s_o)`. One consequence is load-bearing and one is fatal:

- **Load-bearing**: the module's whole retiming collapses to a single scalar
  `delta = r(s_o) - r(s_i)`, which is all the parent needs to know.
- **Fatal**: every input-to-output path through the module gains *the same* `delta`, whether it
  needs it or not.

When a parent has a feedback loop through a child, the loop's register count is invariant under
retiming, and that second consequence bites in two distinct ways. With `loopRegisters` counted in
the parent:

- **Register-count mode** (`delta > loopRegisters`): the child's retiming wants more registers on
  the loop than the loop has, so the loop's external edges would have to go negative. Infeasible
  outright; no clock period helps.
- **Delay-concatenation mode** (`delta == loopRegisters`): satisfiable, but it consumes every
  register in the loop, pinning the external return edge to zero - so the parent then has to fit
  `outputDelay + inputDelay` into one clock period, two maxima over all ports, charging the loop for
  paths that need not be on it.

`netfpga/src/cms/processor.gapl`'s `packet_body_processor` is the only module in CMS with a child
instance on a feedback loop, and the only infeasible one. The child is `count_min_update_sketch4`
(input ports `value`, the long hash path that is *not* on the loop, and `original`, the sketch that
*is*; output `updated`), on a two-node loop holding the single `current_sketch` register:

| period | delta | inputDelay | outputDelay | what binds |
|--------|-------|-----------|------------|------------|
| 20     | 21    | 3         | 11         | register count (21 > 1); the delays would have fit |
| 100    | 3     | 100       | 83         | both (3 > 1, and 183 > 100) |

As the period loosens `delta` shrinks but the delays grow, and there is no period where both modes
pass. Confirmed causally: feeding `updater` from the already-present, commented-out constant
`initial_sketch` instead of `current_sketch` - taking the child off the loop, changing nothing else -
makes CMS compile at period 20 with 858 registers.

## What changes, at a glance

| | today | proposed |
|---|---|---|
| module boundary | super-source + super-sink on every module | port nodes directly; super-nodes only on root modules |
| port lags | all inputs share one lag, all outputs share another | each port has its own lag |
| surfaced to parent | scalar `delta` | vector of per-port lags |
| input/output delay | one of each per module | one of each per port |
| combinational delay | one per module | one per (input port, output port) pair, sparse |
| register count across the module | implicitly 1 per contracted instance | per (input port, output port) pair |
| contracted graph | `c_i`, `c_o`, `d_i`, `d_o`, `d_c` | one node per port, per-port delay nodes, per-pair combinational nodes |

## The boundary summary

Each solved module surfaces, for input ports `i_1..i_m` and output ports `o_1..o_n`:

| quantity | indexed by | defined when |
|---|---|---|
| `lambda(p)` - the port's lag `r_i(p)` | port | always |
| `in(j)` - longest combinational path starting at `i_j` | input port | always |
| `out(k)` - longest combinational path ending at `o_k` | output port | always |
| `W(j,k)` - retimed register count from `i_j` to `o_k` | pair | pair is connected |
| `C(j,k)` - combinational delay from `i_j` to `o_k` | pair | pair connected and `W(j,k) == 0` |

`lambda` is only meaningful up to a global constant - what matters is the differences. Today's
`delta` is the degenerate case: super-nodes force `lambda(i_j) = 0` for every input and
`lambda(o_k) = delta` for every output.

`W(j,k)` is new and is not optional. Without it the parent has no idea how many registers a
register-separated port pair carries; today that is implicitly 1 for every pair, which understates
`W(u,v)` for parent paths crossing the child and over-pipelines around deeply pipelined children.

## The interior problem

Delete the super-node equality constraints. Everything else stays: same objective, same
non-negative-register constraints, same clock-period constraints, children contracted as below,
anchor on any node.

Then read `lambda(p) = r(p)` straight off the solution. `MinimalRegisterSolver.lastSolveNodeLags`
already exposes exactly this.

Extracting the rest is per-port rather than per-module: `findFastestConnectionsFromNode(i_j)` on the
retimed graph gives `in(j)` (max delay over zero-register sinks that are not output ports) and, from
its entries at each output port, `W(j,k)` and `C(j,k)`; the reversed graph from each `o_k` gives
`out(k)`. That is `O(m + n)` shortest-path runs per module instead of 2. GAPL port counts are small
(CMS's updater is 2 in, 1 out), so this is cheap and the `C(j,k)` matrix is small and sparse.

Two things fall out for free:

- **Bug 4 dissolves.** With no super-source there is nothing attaching literals and other
  source-less nodes to the input lag, so they stop being pinned. That pinning was a deliberate fix
  for a bug found in a real application, though - find out what that bug was before assuming this is
  safe. See Open questions.
- **Boundary inference stops mattering.** Summaries are computed from an explicit port list rather
  than from `rootNodes()`/`leafNodes()`, which is the same class of bug as the component-retiming-
  difference one already fixed in `computeTimingProperties`.

## The contracted graph

Per child instance, in the parent's flat graph.

**Nodes** (synthetic; node weight is combinational delay):

- `c(p)`, weight 0 - one per port, the attachment point for the parent's own edges
- `dIn(j)`, weight `in(j)` - one per input port
- `dOut(k)`, weight `out(k)` - one per output port
- `g(j,k)`, weight `C(j,k)` - one per *combinationally connected* pair only

**Edges**, specified by the weight they must have *after* retiming:

| edge | `w_r` | exists when |
|---|---|---|
| `c(i_j) -> dIn(j)` | 0 | always |
| `dOut(k) -> c(o_k)` | 0 | always |
| `dIn(j) -> dOut(k)` | `W(j,k)` | pair connected, `W(j,k) >= 1` |
| `c(i_j) -> g(j,k)` | 0 | pair combinational |
| `g(j,k) -> c(o_k)` | 0 | pair combinational |

**Equality constraints**: the port-lag constraints (next section), plus `r(dIn(j)) = r(c(i_j))`,
`r(dOut(k)) = r(c(o_k))`, `r(g(j,k)) = r(c(o_k))`.

**Starting weights.** Because `W(j,k) = W_orig(j,k) + (lambda(o_k) - lambda(i_j))` by definition of
retiming, working backwards through `w = w_r - (r(sink) - r(source))` collapses to:

```
w(c(i_j) -> dIn(j))   = 0
w(dOut(k) -> c(o_k))  = 0
w(g(j,k) -> c(o_k))   = 0
w(dIn(j) -> dOut(k))  = W_orig(j,k)
w(c(i_j) -> g(j,k))   = W_orig(j,k)
```

That is, **every contracted edge starts at the child's pre-retiming port-pair register count**, and
the equality constraints carry everything about what the child's retiming actually did. This is much
cleaner than today's `-delta` / `-delta + 1`, and it means the "unretimed" model that
`computeUnretimedProperties` currently has to build separately for stats falls out of the same
construction with `lambda = 0`. Sanity check against today: a single `delta` with a combinational
pair means `W(j,k) = 0`, so `W_orig = -delta` - exactly the current `-retimingDifference` weight on
the `c_i -> d_c` edge.

Two properties are worth calling out because they are what make this work:

- **`dIn(j)` dangles as a sink and `dOut(k)` dangles as a source** for register-separated pairs.
  That is deliberate - they mean "a path from this port that ends at a register" and "a path from a
  register that ends at this port." Nothing forces `in(j)` and `out(k)` into one combinational
  stretch unless they belong to a genuinely combinational pair. This is what removes the
  delay-concatenation failure mode.
- **`g(j,k)` exists only for genuinely combinational pairs**, so a parent-side path from `o_k` back
  to `i_j` closes a zero-weight cycle only when the child really does couple those two
  combinationally. This is what removes the false-combinational-loop crash.

Loop register invariance is still enforced - not through an edge, but through the port-lag
equalities, which close the lag arithmetic around any cycle passing through the instance.

## The exterior constraints

For each child instance, pick a deterministic reference port `p_0` and emit, for every other port
`p`:

```
r_e(c(p)) - r_e(c(p_0)) = lambda(p) - lambda(p_0)
```

`|P| - 1` equality constraints per instance instead of today's one. The single free translation
constant `t` per instance is exactly the `t` in the existing correctness argument.

For CMS's updater this is `lambda(value) = 0`, `lambda(original) = 21`, `lambda(updated) = 21` -
"present the sketch 21 cycles after the data," which is the correct hardware and what monolithic
retiming already builds. In the parent, `r_e(c(updated)) - r_e(c(original)) = 0`, so the loop gains
nothing and stays feasible; the 21 cycles land on the `i.valid` path instead, where they belong.

## The top-level boundary

At **root modules only** - `InvocationGraph` roots, not every module the way
`HierarchicalRetimingProblem` currently treats them - re-add a super-source and super-sink with
`r(i_j) = r(s_i)` and `r(o_k) = r(s_o)`.

The point is **port alignment at the design boundary**. Retiming already guarantees the property
that sounds like it needs guaranteeing: reconvergent paths keep equal register counts automatically,
because every path from `u` to `v` shifts by the same `r(v) - r(u)`. What it does not guarantee is
that the design's own ports stay mutually aligned, and that is exactly what the super-nodes buy - an
external interface where all inputs are consumed at one pipeline stage and all outputs produced at
another, instead of one where `data` must lead `valid` by 21 cycles.

Two bonuses:

- `r(s_o) - r(s_i)` is the design's added latency, currently unconstrained. Pinning it to zero with
  an `s_o -> s_i` back edge is what `--retiming-maintains-timing` already does monolithically
  (`NetlistLeisersonCircuitConverter.fromModule`'s `loopEdge`), and this design makes it supportable
  hierarchically for the first time - `Retimer.transform` currently rejects the combination outright.
- Port alignment vs. port skew becomes an explicit per-module policy rather than a global
  assumption baked into the graph construction.

## Why it stays correct

The existing argument (paper section "Correctness of Hierarchical Retiming") generalises with a
vector where there is currently a scalar; the structure does not change.

Today: `r(v) = r_i(v) + t` for `v` in the child, `r(v) = r_e(v)` outside, with
`t = r_e(c_i) - r_i(s_i)`. The four-case non-negativity argument turns on `r(i_j) = r_e(c_i)`, which
holds because both sides pin all their ports together.

Proposed: same composition, with `t` defined against the reference port,
`t = r_e(c(p_0)) - lambda(p_0)`. The exterior constraints then give `r_e(c(p)) = lambda(p) + t` for
*every* port, so `r(p) = r_e(c(p))` still holds port by port, and the same four cases go through
unchanged - only now each boundary edge matches against its own port's node rather than a shared
one.

The clock-period argument gets *easier* to state, not harder. Today's case 3 ("part of the path is
in `G`") has to appeal to module-wide maxima; with per-port nodes each case names the specific
`dIn(j)`, `g(j,k)` or `dOut(k)` the path actually runs through. The three sub-cases become:

- enters at `i_j`, terminates inside: bounded by `in(j)` via `c(i_j) -> dIn(j)`
- starts inside, exits at `o_k`: bounded by `out(k)` via `dOut(k) -> c(o_k)`
- crosses from `i_j` to `o_k` combinationally: bounded by `C(j,k)` via `g(j,k)`, which exists
  precisely when such a path exists

The step today's proof leaves implicit - that the corresponding path in the exterior graph is also
register-free after retiming - is still the load-bearing one, and is still discharged by the
`w_r = 0` requirements in the table above. Worth stating explicitly this time.

## Implementation plan

Ordered so each step is independently testable.

1. **Make the port list explicit on the graph type.** `HierarchicalLeisersonCircuitGraph` currently
   carries `rootAttachment`/`leafAttachment`. Replace with ordered input-port and output-port node
   lists. This is an analyzer-side type change (`analyzer/.../util/graph/`) that `flatten()`,
   `flattenToWeightedGraph()`, and the converters all touch.
2. **Stop building super-nodes in the converter.**
   `HierarchicalNetlistLeisersonCircuitConverter.fromModule` drops `superInputNode`/`superOutputNode`
   and populates the port lists from `getInputNodes()`/`getOutputNodes()` instead. Note this also
   drops the source-less-node attachment (Bug 4) - see Open questions first.
3. **Generalise `TimingProperties` to a per-port/per-pair summary type**, and rewrite
   `computeTimingProperties` to take the port lists. It already takes explicit boundary nodes after
   the current round of fixes, so this is a widening rather than a rewrite.
4. **Rewrite the contracted-graph construction** in `HierarchicalMinimalRegisterSolver.buildFlatModel`
   per the tables above. `ChildSummary` grows from three scalars to the per-port summary;
   `ChildExpansion` grows from five fixed node slots to per-port/per-pair maps.
5. **Emit the port-lag equality constraints** in place of the single `retimingDifference` constraint.
   `NodeEqualityConstraint` needs no change.
6. **Add root-only super-nodes.** `HierarchicalRetimer`/`HierarchicalRetimingProblem` need to
   distinguish genuine `InvocationGraph` roots from every module in the batch, which they currently
   do not. Apply the alignment constraints only there.
7. **Make the boundary constraint structural, not type-based.** `MinimalRegisterSolver` step 6
   currently finds boundary edges with `it.source.value is VirtualIONode` - a netlist type check
   inside an otherwise value-generic solver, which means *no* test in
   `compiler/src/test/kotlin/graph/` (all `String`-valued) exercises it. Pass the constraint in
   explicitly instead. Do this before step 6 so the new behaviour is testable at all.
8. **Optional, separable**: enable `--retiming-maintains-timing` for hierarchical mode now that the
   top-level boundary exists.

## What this fixes and what it does not

Fixes:

- CMS and the register-count failure mode - the reason for the whole exercise
- the delay-concatenation failure mode (the 15-line repro in `todo.md`)
- the false-combinational-loop crash on legal acyclic designs
- the implicit `W = 1` understatement for register-separated port pairs
- Bug 4's literal pinning, as a side effect
- `--retiming-maintains-timing` under hierarchical mode, if step 8 is taken

Does not fix:

- **Hierarchical is still not optimal, and still not complete.** A module is retimed once and reused,
  so its `lambda` vector is chosen without knowledge of any particular call site. A design where two
  call sites want incompatible port lags is still infeasible hierarchically and fine monolithically.
  This is the irreducible cost of preserving module boundaries.
- **Granularity is per port, not per field.** A record port like `netfpga_packet_body` is one
  `InputNode` carrying data/valid/keep/last, so those cannot be skewed relative to each other. CMS
  is fine (the updater's `value` and `original` are separate ports), and this matches the coarseness
  `CombinationalLoopDetector` already assumes, but a design needing field-level skew will not get it.
- **Register reset values.** Retiming already does not recompute them -
  `verilator-test/tests/cyclic-retiming` is marked deprecated for exactly this. Skew puts more
  registers in more places, so this will bite in more designs. Orthogonal, but worth tracking.
- **Hierarchical search performance.** `solveOrNull` still re-solves the whole hierarchy per
  `findMinimumClockPeriod` probe, and `computePossibleClockPeriods` still fully flattens every
  module. Unchanged by this work.

## Open questions

- **What was the bug that motivated attaching every source-less node to the super-source?** Step 2
  removes that attachment. It needs to be understood before it is dropped, not after.
- **Does anything downstream assume a module's ports are lag-aligned?** Verilog emission looks
  unaffected - the skew is a contract between parent and child enforced by the equality constraints,
  and a child's Verilog is still just its retimed graph - but `simengine`, `simgen` and the netfpga
  wrappers should be checked, since they interact with module boundaries directly.
- **Reference port choice must be deterministic** or generated Verilog stops being reproducible.
  First input port in declaration order is the obvious choice.
- **Is per-pair `W(j,k)` worth the extra edges?** It is strictly more accurate than today's implicit
  1, but it adds up to `m * n` edges per instance. Cheap at GAPL's port counts; measure on aes/md5
  before assuming.

## Validation

- The 15-line repro in `todo.md` must go from "infeasible at periods 1-3" to feasible wherever
  monolithic is.
- The false-loop repro in `todo.md` must compile instead of throwing
  `IllegalArgumentException: Graph cannot contain zero-weight cycles`.
- CMS at period 20 must become feasible without the `initial_sketch` workaround, and should be
  compared against the known-good monolithic register count.
- `md5`, `aes` and `dag-retiming` under `--flatten none` are byte-identical between the current tree
  and `43acbcd` today; they will *not* stay identical through this change (that is the point), so
  they need to be re-validated against monolithic behaviour and through `verilator-test` rather than
  by diffing Verilog.
- The two hierarchical regression tests added alongside the boundary-tracking fix
  (`parent balances a child's added latency{, when the child has an unused internal value}`) must
  keep passing - they pin the component-retiming-difference behaviour that the `lambda` vector
  generalises.
- Step 7 first: until the boundary constraint is structural rather than a `VirtualIONode` check,
  none of the graph-package tests exercise it, so anything asserted there about boundary behaviour
  is currently vacuous.
