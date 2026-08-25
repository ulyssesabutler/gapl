# Protocols

## Goals

The interfaces for hardware modules are typically defined by the set of wires that are used to send and receive data to and from the module, as well as how those wires are organized.
This interface definition doesn't actually provide much information on how data is represented in hardware.

This creates a gap in these definitions that makes it difficult to simulate and test hardware modules and check for correctness.
We'd like to introduce formal semantics to track the individual datum that are important for the business logic of the application.

Once we've formally defined the bounds of a individual datum, we can reason about the scope of execution for a function.

## Overview

We do this by defining a protocol for how an individual datum is transmitted between modules.
In GAPL, this protocol is defined in three parts.

1. _**Spacial**_: How is data spread over _space_?
This requires the same information that existing hardware modules already provide.
That is, what wires does the module use and how are they organized.
2. _**Temporal**_: How is data spread over _time_?
Specifically, how is the data spread across beats?
Not all data can be transmitted in a single clock cycles.
When data requires multiple transmissions to send, each transmission is called a _beat_.
Here, we want the protocol to define what information is transmitted in each beat.
3. _**Timing**_: On which clock cycle is information transferred for a beat?
Both the producer and consumer have to agree, and this is an implicit part of the protocol.
The consumer needs to know when the incoming data is available, and the producer needs to know when the consumer has captured the data so it can move on to the next beat.

Let's look at the AXI-Stream protocol as an example.
A Verilog module using an AXI-Stream interface will use 4 ports.
The `data` port contains the actual information.
In NetFPGA, it's 32-bytes wide.
But the AXI-Stream protocol allows us to send much longer streams of data.
All data across the AXI-Stream is uniform.
The end of the stream is signaled by the `last` flag.
Finally, the `valid` and `ready` flags are used by the producer and consumer, respectively, to indicate to each other when a transmission can occur.
The AXI-Stream protocol dictates that, when both flags are present, a transmission can occur.

So, the spacial dimension of this protocol is the 32-byte `data` wire.
The temporal dimension captures the fact that this is a uniform stream of data whose end is signaled by an explicit flag, `last`.
Finally, the timing dimension captures the handshake between the producer and consumer, the clock cycle of transmission is that when both `valid` and `ready` are set.