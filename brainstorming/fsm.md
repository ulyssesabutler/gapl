# FSMs in GAPL

## Survey

We ground this work by exploring the existing set of FSMs we've created for the GAPL project, as well as a few additional FSMs that we'd like to create.
Most of these FSMs are from the GAPL wrapper, written in Verilog.
A few are from the planned MD5 and AES-CTR applications.

We've further categorized these FSMs by how they could be implemented using planned GAPL features.

| FSM Kind                        | Module / Application                                | Covered By     |
|---------------------------------|-----------------------------------------------------|----------------|
| Position within lifecycle       | `packer`, `packet_processor`, `trim_front`          | Layer 2        |
| Accumulation                    | `axis_parser`, `byte_count`, `AES-CTR`              | `scan` state   |
| Conditional trailing emission   | `axis_pad_output`, `MD5_pad`                        | new stdlib fun |
| Per-lifecycle reset             | `mutual_exclusion`, packet_parser`                  | Layer 2        |
| Flow control / stall            | `processor_controller`, `queue`                     | Layer 3        |
| Value held alongside stream     | `axis_hold_metadata`, `axis_user_tracker`           | Layer 3        |
| Multi-stream merge / interleave | `axis_3_to_1_arbiter`, `axis_transmission_combiner` | Layer 3        |

Generally, two different systems will be used to replace these hand-coded FSMs in GAPL.
First, the new GAPL protocol system (split into three layers) will replace those FSMs that track the lifecycle of a piece of data as it flows through the system and coordinate the flow of streams.
Second, business logic will be implemented as Mealy machines using `scan` and `fold`.

While the protocol system in GAPL is still being developed (detailed below), the GAPL stdlib already contains `scan` and `fold` functions.
There are two problems with these functions that need to be addressed.

1. These functions aren't equipped to handle a stream over its lifecycle.
    - This will be addressed in layer 2 of the new protocol system.
2. Building a mux/demux to handle branch on the state is very verbose.
    - Rather than make Mealy machines a first class citizen, we'll instead add syntactic sugar to help create the mux/demux.

## Protocol Layers

We're primarily interested in processing data transferred between modules over multiple clock cycles.
This proposal will give users the ability to describe the lifecycle of their data in terms of beats.
That is, those clock cycles over which data is actually transferred.
We call this description the data protocol.

The protocol description is split into three layers.

1. Layer 1 - Spacial
2. Layer 2 - Temporal
3. Layer 3 - Timing

Layer 1 describes the physical set of wires and ways to interpret those wires.
That is, it describes how the data is spread over the wires on the chip.
Layer 2 describes the sequence of beats that make up a valid lifecycle for the data.
Finally, layer 3 describes the timing of those beats.
That is, the clock cycle on which the data is transferred.

### Layer 1

Layer 1 consists of two parts.
It describes the physical wires and ways to interpret those wires.
The description of the physical wires is done using the existing GAPL `interface` feature.

```
interface axi_beat {
    data: byte[size];
    keep: boolean[size];
    user: wire[128];
}
```

Interpretations of those wires can be defined using the new `view` feature.

```
view ethernet_header: axi_beat {
    dest_mac:   data[0:5];
    src_mac:    data[6:11];
    ether_type: data[12:13];
}

view ip_header: axi_beat { ... }
```

### Layer 2

Layer 2 describes the sequence of beats that make up a valid lifecycle for the data.
These descriptions can be made using the new `datatype` feature.

These `datatypes` can be constructed using
- `seq` - A list of phases, occurring in listed order.
- `stream` - Homogenous phases, occurring until a `last` flag is reached.
- `infinite` - Homogenous phases, repeating indefinitely.
- `repeat` - Homogenous phases, repeating a predefined number of times.
- `choice` - A list of phases, only one of which can occur.
- `null` - A phase that does nothing.
- `single` - An implicit phase that contains a single beat. Created by naming the view.

Nesting is permitted.

These `datatype`s can be thought of as a regular expression over the alphabet of views.

```
datatype packet: axi_beat seq {
    eth: ethernet_header;
    ip: ip_header;
    body: choice {
        empty: null;
        nonempty: stream(axi_beat);
    }
}
```

NOTE: Datatypes for external function cannot use views.

### Layer 3

Our initial version will contain two predefined timing models.
When each function is defined, it's assigned to a default timing model.

1. `@dataflow` All data is always valid. These functions could, in theory, be combinational. Though, in practice, they will often be retimed to be pipelined.
2. `@valid` Data is only transferred when the producer signals it has valid data. In other words, the producer is allowed to stall.
3. `@ready_valid` Data is only transferred when the producer signals it has valid data and the consumer signals it is ready to receive. In other words, both the producer and consumer are allowed to stall.

The primary challenge in implementing `@valid` and `@ready_valid` is the fork-join problem.
If implemented as a straight pipeline, this would be fairly straightforward.

In general, `@valid` is allowed to fork, but not join, and `@ready_valid` is neither allowed to fork nor join.
Instead, GAPL offers a few features that allow users to implement logic that would require a fork-join.
One method that works for both models is promotion.
The logic that requires a fork-join structure is defined as a `@datapath`.

```
function helper()@datapath i: wire => o: wire { ... }

function main()@valid i: wire => o: wire {
    i => helper()@valid => o;
}

function main()@vready_valid i: wire => o: wire {
    i => helper()@ready_valid => o;
}
```

When promoting a function to `@valid`, a parallel `valid` signal is added to the whole function (that will be retimed with that function).

The process for promoting to `@ready_valid` is a bit more involved.
Here, an "island" is created in hardware, similar to the island the GAPL wrapper creates in the NetFPGA pipeline.
The GAPL kernel, essentially, utilizes `@valid` timing model, and the wrapper creates an island the rest of the pipeline can interact with using its `@ready_valid`.

For `@ready_valid`, we'll also include a set of standard library functions to cover the most common fork-join patterns.
For example, on the fork side, we can imagine including different arbiters that send each piece of data to a different consumer.
We could also imagine another function which duplicates each piece of data to each consumer.
On the join side, we have even more possibilities.
When joining two streams, we could use a `zip` if both streams are the same size (something the user will need to be careful of) or `align` if they're different sizes.
We could also imagine a join which concatenates two streams together, or functions that interweave data from two streams.

## Mealy Machines

While the protocol definition systems we've described here are versatile enough to replace the majority of the FSMs in the GAPL corpus that exist today, there are still a few FSMs used to define business logic that have to be written by hand.
These can be designed as a Mealy machine.
GAPL will process the incoming data as a stream by using either the `scan` (if we want to emit an output on each input) or `fold` (if we only want to emit a single output at the end) functions.
Both of these functions take, as an argument, a state value, and return a updated state value.

To construct a Mealy machine, a user needs to define the shape of this state, and define the logic based on the current value of that state.
GAPL contains all the primitives that are necessary to implement this branching logic, but doing so can be quite verbose.

Let's take a look at the priority router as an example.
Priority routers are ways to create hardware that exhibits an `if`/`else if`/`else` behavior.
They are defined in GAPL as follows:

```
interface conditional(T: interface) {
    conditional: boolean;
    value: T;
}

funciton priority(T: interface, conditionalCount: integer) conditionals: conditional(T)[conditionalCount], default: T => output: T
```

So, to create a priority router, you'd have to write something like this:

```
declare conditionals: conditional(T)[conditionalCount];

state, condition0 => equals() => declare conditional0: boolean;
conditional0 => conditionals[0].conditional;
value0 => condition[0].value;

state, condition1 => equals() => declare conditional1: boolean;
conditional1 => conditionals[1].conditional;
value1 => condition[1].value;

state, condition2 => equals() => declare conditional2: boolean;
conditional2 => conditionals[2].conditional;
value2 => condition[2].value;

conditionals, default => priority(T, 3) => declare output: T;
```

In a more idiomatic language, defining similar logic might look like

```
output = when (state) {
   condition0 => value0;
   condition1 => value1;
   condition2 => value2;
}
```

GAPLs syntax also makes it difficult to set multiple values at once.
Logic that requires setting, for example, three values on each branch would normally require the above machinery to be duplicated for value.
A more efficient syntax might look something like

```
when (state) {
   condition0 => {
       output0 = value00;
       output1 = value01;
       output2 = value02;
   }
   condition1 => {
       output0 = value10;
       output1 = value11;
       output2 = value12;
   }
   condition2 => {
       output0 = value20;
       output1 = value21;
       output2 = value22;
   }
}
```

Rather than make Mealy machines a first class citizen, we instead want to revise the grammar to make defining the branches of a priority router easier and less verbose to define.