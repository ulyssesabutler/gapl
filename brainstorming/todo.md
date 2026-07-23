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
- `HierarchicalRetimer`'s per-module timing stats (`unretimedGraphStats`/`retimedGraphStats`,
  used only for `printAllGraphStats` logging) are computed by calling `computeTimingProperties`
  directly on `.flatten()`ed before/after graphs from outside the solver. This still feels
  architecturally awkward - a solver-external caller reaching back into solver-internal
  machinery for stats derived from the solve - and should be revisited. It's also a slightly
  different (generic-flatten-based) computation than the one `HierarchicalMinimalRegisterSolver`
  uses internally for its own per-level expansion math, so the two are not guaranteed to agree
  bit-for-bit, only in spirit.
- `SccSolver` does not enforce the zero-register `VirtualIONode` boundary invariant that
  `MinimalRegisterSolver` enforces (classical host-vertex retiming) - the vendored `retimeByScc`
  has no equality-constraint mechanism. Acceptable for now since it's feasibility-only (like
  `FastSolver`), but a real gap if `scc` is ever used as the final/register-shaping solver
  against real module I/O boundaries.
- `HierarchicalRetimer` hardcodes `HierarchicalMinimalRegisterSolver` internally (for both the
  min-clock-period search and the final solve) instead of taking a resolved `HierarchicalSolver`
  - fine while it's the only implementation, but should take an injected/resolved solver once a
  second `HierarchicalSolver` exists.
- The vendored reference algorithms repo at
  `compiler/src/main/kotlin/.../retiming/solver/retiming/` (source for `SccSolver`) is a nested
  git repo, currently untracked and not vendored properly (no submodule, no committed copy) -
  deliberately deferred. Plan is to eventually extract only the relevant portions into this
  codebase properly, rather than depending on the whole standalone repo as-is.