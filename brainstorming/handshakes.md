# Handshakes

## Overview

Earlier, I had this notion of function IO "types."
My initial thought was to have input either be `combinational` or `sequential`.
A `sequential` input might be something like an AXI-stream interface.
While a `combinational` input would just be raw wires.

I also briefly considered making a `stream` function type.

The idea behind these "types" has always been to allow the user to specify how data is fed into the module, how data is transferred between modules.
That said, once this is specified in the signature, the user could use the body of the module to focus on the implementation details.
Maybe the body could be focused almost purely on dataflow.

I think a better way to do this might be to have the function IO "types" represent handshakes.

I think the inputs and outputs of all modules are essentially "streams," even if they don't have explicit `ready`, `valid`, and `last` wires.
After each module finishes processing the current input, it moves onto the next input.
In essence, all modules are processing an infinite "stream" of data.

I wonder if it might be better to think of function IO "types" as just implementations of handshakes.

Combinational circuits don't have a handshake.
The inputs and outputs are always valid.
So their type might just be `raw`, or something similar.

Latency-sensitive modules might have a `go` interface.
Here, timing controls scheduling, rather than explicit signals.
Careful construction of the module ensure that data is never provided when the consumer is not ready.
However, an explicit signal indicating that the current value is valid (or useful) might be helpful.
For example, we might consider an ALU?
We might call this a `signal` handshake?
Although, that term is a bit overloaded.

An important question is, how do users define handshakes?

## Proposed "pre-built" Handshakes

- `raw`
- `signal`
- `stream`
