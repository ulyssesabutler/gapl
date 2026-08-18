# Design Patterns

This document is a collection of design patterns we've seen in the wild

## Temporal References
- In a pipeline (a module that takes $n$ clock cycles, create a register for each cycle)
- These registers are named based on the value and the clock cycle.
  - Effectively a shift register.
- The code then references values at different points in this pipeline.

## Interface Passing
- Let's say we declare a module in the top-level module. Maybe a RAM controller.
- This controller has multiple buses.
  - Each bus is used by a different part of the design.
- So, we often see each level of the module hierarchy with these wires.
  - Data travels up and down the hierarchy

## Controller Wrappers
- For example, for a RAM or USB controller

## Cross-Clock Domains
- Something we haven't really thought about in gapl.

## Server / Client
In Pigasus, this terminology was used to differentiate the sender (client) and receiver (server)

## Probes for Stats
Something else that gapl doesn't really support right now.

This would look something like

```text
a => b, stats_for_a;
b => c, stats_for_b;
c => d, stats_for_c;
// ...
```

