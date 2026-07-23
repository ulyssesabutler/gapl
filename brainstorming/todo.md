# TODOs

## Validation
There are a few different validations we need to do, but currently don't.

### Type checking
- We need to validate that every connection connects two compatible types
  - E.g., only connect two wire vectors if they're the same length
- We need to validate parameters
  - There is a bit of complication here, specifically, with function parameters.
    - These might depend on other parameters to evaluate

## Retiming
- Naively calling `HierarchicalLeisersonCircuitGraph.flatten()` on an already-*retimed* graph is
  unsafe, not just "architecturally awkward" - it was originally tried for both
  `HierarchicalRetimingProblem`-generic clock-period lookups and `HierarchicalRetimer`'s stats
  logging, and both crashed in practice (`IllegalArgumentException: Graph cannot contain
  zero-weight cycles`) on a real hierarchical `-retiming-clock-period min` run. The retimed
  boundary edge weights `HierarchicalMinimalRegisterSolver` computes are calibrated against its
  synthetic per-level "expansion" topology (phantom input/output/combinational-delay nodes encoding
  the child's already-computed retiming difference), not the child's real internal structure, so
  splicing the real structure back in during a naive flatten can produce something that looks
  like an illegal zero-register cycle even though the retiming itself is correct. Fixed by (a)
  `findMinimumClockPeriod` no longer calling `result.computeClockPeriod()` on a solved result -
  it caches from the tried period instead, which is always correct by monotonicity, just a
  slightly more conservative cache-fill than exploiting an over-achieving solver; and (b)
  `HierarchicalMinimalRegisterSolver` now exposes `timingPropertiesFromLastSolve(root)`, sourcing
  `HierarchicalRetimer`'s stats from the properties actually computed during the solve rather than
  re-deriving them externally. `HierarchicalRetimingProblem.computeClockPeriod()`/
  `computePossibleClockPeriods()` (which still do naively flatten) are only ever safe to call on
  the pristine, never-retimed problem now - worth a stronger type-level guard than a comment if
  this bites again.
- `SccSolver`/`DagSolver` do not enforce the zero-register `VirtualIONode` boundary invariant that
  `MinimalRegisterSolver` enforces (classical host-vertex retiming) - the vendored `retimeByScc`/
  `dagRetime` have no equality-constraint mechanism. Acceptable for now since both are
  feasibility-only (like `FastSolver`), but a real gap if either is ever used as the
  final/register-shaping solver against real module I/O boundaries.
- `DagSolver` (wraps `retiming.dagRetime`) is narrower than every other monolithic solver here: it
  requires the *entire* circuit to be acyclic, not just well-formed - a cycle with a register
  protecting it (the normal shape of any real stateful/feedback circuit) is out of its domain
  entirely, not just a harder case. `DagSolver.solveOrNull` throws (not returns null) if the graph
  isn't a true DAG, since that's a solver/graph-shape mismatch, distinct from a target period being
  genuinely infeasible. Only useful for pure combinational-pipeline circuits with no loops at all.
- `HierarchicalRetimer` hardcodes `HierarchicalMinimalRegisterSolver` internally (for both the
  min-clock-period search and the final solve) instead of taking a resolved `HierarchicalSolver`
  - fine while it's the only implementation, but should take an injected/resolved solver once a
  second `HierarchicalSolver` exists.
- The vendored reference algorithms (source for `SccSolver`/`DagSolver`) now live at
  `compiler/src/main/kotlin/.../retiming/solver/anirudhsk/`, committed directly (no more nested
  git repo/submodule). The files still declare `package retiming` rather than a package matching
  their directory - harmless (Kotlin doesn't require the two to match), but worth cleaning up
  if these are ever touched again.