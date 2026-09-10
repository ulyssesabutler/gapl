# Future Work

- Eliminate bugs
  - https://dl.acm.org/doi/abs/10.1145/3503222.3507701
- NFAs
- How to allow the user to define arbitrary handshakes.
- Parameter interfaces
  - Sets of parameters are defined by the users.
  - If two functions share the same parameter interfaces, then `a() => b()` will automatically use the same parameters from `a` in `b`.
- Interface References
  - One common patter is a top level module "passing" the wires from a top level IP block down to a module who uses it.
  - We should think of a cleaner way to support this
- A build system / imports
  - This might also include a vague concept of visibility
- Type system: layers 2 and 3 — see `brainstorming/claude/type-system.md` for the full design,
  decisions, and rejected alternatives. Deferred items from that work, roughly in priority order:
  - **Alternation in the lifecycle language.** No `|` constructor, so parsers (structure chosen by
    content, à la P4 `select`) are inexpressible. Also closes the long-standing "packet length computed
    from a parsed value" request in `brainstorming/archive/protocols.md:193`. Fastest partial path is
    sum types + `match`, which works under today's GAPL.
  - **Rate relationships between two ports.** No syntax for "these two streams advance together" or
    "one beat of A per four of B." Blocks two-stream joins, `zip`, and any `regroup` whose ratio is not
    static. A generated join silently asserts 1:1; violation shows up as deadlock, not a type error.
  - **`map` is overloaded** — beat-wise (combinational op, generates logic) vs. per-lifecycle (op is a
    whole pipeline, generates nothing). Deliberately deferred; needs distinct names.
  - **Direction reversal / control plane.** No mechanism for a host to write a rule table or config
    register (AXI4-Lite style: address, write-enable, request/response). Information flow reverses by
    phase, which the unidirectional lifecycle language cannot express. This is the deferred
    session-types case. (An `optional<T>` sketch was recalled as reaching for this during the design
    conversation that produced `type-system.md`, but it could not be located anywhere in the current
    tree — flagged, not resolved.)
  - **Memory primitive.** Random access is not expressible as `scan` under any encoding. Blocks Bloom
    filters, realistic sketch sizes, per-flow state. Will need read-modify-write forwarding for
    consecutive same-address updates.
  - **Time-multiplexing combinator** with an explicit initiation interval. Everything currently unrolls
    spatially; this is where resource sharing gets recovered.
  - **The `@ready_valid` downgrade pass** — prove nothing downstream stalls and strip the handshake.
    Without it, the first end-to-end comparison against hand-written Verilog looks bad for a
    non-fundamental reason.
  - **Per-port protocol overrides.** Deliberately deferred until a compelling case appears.
  - **C-slow retiming.** The fix for the unpipelineable `fold` recurrence (see
    `brainstorming/claude/md5-lifecycle-sketch.md` finding 3). Multiplies registers around cycles, then
    retimes; builds on the existing `HierarchicalLeisersonCircuitGraph` machinery.
- **Stale syntax in the design docs.** `brainstorming/archive/interfaces.md` and several
  `gapl-example/wip/*.gapl` files use `<T>` generics and a `parameter` keyword that do **not** match
  `antlr/src/main/antlr/CST.g4`. Real syntax: `interface pair(T: interface, U: interface)`,
  `function repeat(I: interface, factor: integer, operation: I => I)`. Either fix them or add a header
  warning — an agent or a new contributor reading those files will confidently write invalid GAPL.
- **`SccSolver.kt:18-20` and `DagSolver.kt:22-23`** both note they do not enforce the zero-register
  `VirtualIONode` boundary invariant that `MinimalRegisterSolver` does. Unverified whether that matters
  for where those solvers are actually used — worth confirming it is deliberate.