# Protocols

## Overview

Earlier, I had this notion of function IO "types."
My initial thought was to have input either be `combinational` or `sequential`.
A `sequential` input might be something like an AXI-stream interface.
While a `combinational` input would just be raw wires.

I also briefly considered making a `stream` function type.

The idea behind these "types" has always been to allow the user to specify how data is fed into the module, how data is transferred between modules.
Once this is specified in the signature, the user could use the body of the module to focus on the implementation details.
Maybe the body could be focused almost purely on dataflow.

I think a better way to do this might be to have the function IO "types" represent some idea of "protocols."
That is, I think figuring out how data is processed, how the answer to any given query is computed, is separate from the determining how modules interact with each other.
Similarly, in gapl, I would like these to be two separate concerns.
When the user is describing a function, they should just focus on data.
A user should focus on things like timing behavior separately.

This introduces at least one obvious problem.
If the user isn't thinking about the timing behavior of their system while describing the dataflow, it might make them more likely to describe a dataflow with inherently inefficient timing characteristics.
Maybe we should think about applying some form of algorithm analysis here?

I think the inputs and outputs of all modules are essentially "streams," even if they don't have explicit `ready`, `valid`, and `last` wires.
After each module finishes processing the current input, it moves onto the next input.
In essence, all modules are processing an infinite "stream" of data.

## What are the different parts of a protocol?

What do we have to figure out if we want to define a protocol?

I think it's just the following three things.

### What data is involved in each request?
- E.g.,
  - two input int and an output int for an adder
  - An input header and an output header for a switch
  - An infinite stream of input and output bytes

### How is the data of a single request represented?
- That is, what is the input and output interface, and how does it relate to the underlying data?
  - How is data split across multiple transmissions?
- Specifically, what does each transmission look like?
- How should each transmission be represented? Which transmissions are valid? Which bytes are valid? How do we know when the last transmission is?
  - It might be a predefined value (e.g., 8 transmission of 8 bytes each)
  - It might be a stream with an explicit last flag (e.g., a packet of variable length)

### When is each transmission made?
- This is commonly handled in a few ways
  - Static pipelines are specified by specifying the timing characteristics of modules in number of clock cycles.
    - Initiation interval, pipeline length, etc.
  - Latency insensitive
    - A valid/ready handshake is used
      - Sometimes one input corresponds to one output, but this isn't always the case (shape of the data?)

All three of these might affect what the interface of the data looks like.
Though, their implementation might be implicit.
For example, the last transmission of a piece of data might be transmitted with an explicit `last` flag, or it might be implicit with a number of transmissions.

## Handshakes
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

## Streams

We also briefly discussed how existing frameworks don't do a good job of differentiating between functions that process just a single value and functions that process a stream of values.

Let's discuss, for example, the two functions that made up our `string_matching` example.
That is, the `string_matching` function itself, and `string_equals`.
Specifically, let's look at the two function signatures.

```text
function string_matching_string_equals(
    parameter string_size: integer
) strings: pair<string(string_size), string(string_size)>() => result: boolean()

function string_matching(
    parameter needle_size: integer
) needle: string(needle_size), heystack: char() => result: boolean()
```

Let's specifically focus on the `string_matching` function.
There are two inputs, `needle` and `heystack`.
What's not clear is that, for any given request, only a single needle is provided, while a stream of characters make up the heystack.

In both cases, the function signature only shows what data will be available on any given clock cycle.
Additional information concerning _how_ the data is provided to the function is inferred, perhaps by reading the function definition.

### Proposed "pre-built" Handshakes

- `raw`
- `signal`
- `stream`

## Data types

One idea is to try and combine all of these ideas (that is, those discussed in "What are the different parts of a protocol?")

We could start by defining the interface (that is, the physical set of wires).
This would probably look identical to the interfaces that currently exist in gapl.

### Transmission Definition
We could then define how each individual transmission looks as the states in a state machine.
This might look something like a block that defines different states using a name and a condition.
```text
transmission {
    first: once;
    second: repeat(5);
    footer: until(equals(last, 1));
}
```

With a general form of
```text
transmission {
  [STATE_NAME]: [END_CONDITION];
}
```
The compiler will construct a small FSM, with the states occuring in the order they're declared.
It's worth noting that this introduces a novel requirement in the gapl compiler.
Here, the order of the statements actually matters.

In the meantime, I think the list of possible condition types should just be
- `once`: Always just a single transmission. Equivalent to `repeat(1)`.
- `repeat(n)`: Use this state for `n` transmissions.
- `until(c)`: Use this until the condition `c` is met, where `c` is some boolean function on some set of wires.
  - E.g., `last, literal(1, 1) => function equals()`
  - This will require us to be able to reference signals in the interface.
- `infinite`: An endless stream of data

#### Transmission of Datatypes

This can be extended a bit by giving transmissions "datatypes."
This might look something like...
```text
interface packet_buffer {
    data: wire[8];
    section_last: boolean;
    packet_last: boolean;
}

datatype word {
    interface packet_buffer;
    trasmissions: repeat(4);
}

datatype section {
    interface packet_buffer;
    transmission: datatype word until(equals(section_last, 1));
}

datatype header {
    interface packet_buffer;
    
    transmissions {
        type: once;
        source_address: datatype word once;
        destination_address: datatype word once;
    }
}

datatype body {
    interface packet_buffer;
    transmissions: datatype section until(equals(packet_last, 1));
}

datatype packet {
    interface packet_buffer;
    transmissions: {
        header: datatype header once;
        body: datatype body once;
    }
}


```

One format that would be nice to support, but I'm not sure what the format would look like, would be a format where the length of the packet is computed based on a parsed value.
For example, we might have
```text
transmission {
    header: ...
    body: ...
}
```
where the header contains a value (maybe a slice of the data port?) that indicates the number of transmissions that make up the body.
So, we would need one section with instructions on how to parse the value, and then the 

### Datatype examples
In this first example, the data being sent is always 1024 bits, sent in 4 batches of 256.
```text
datatype packet {
    interface {
        data: wire[256];
    }
    
    transmissions {
        // The packet is made up of 4 transmission of 256 bits each
        main: repeat(4);
    }
}
```

In this second example, the data is still 1280 bits, with the first 256 being the header and the next 1024 being the body.
```text
datatype packet {
    interface {
        data: wire[256];
    }
    
    transmissions {
        header: once;
        body: repeat(4);
    }
}
```

In this example, the header and body are both sent 32 bits at a time, but both are of variable length.
```text
datatype packet {
    interface {
        data: wire[256];
        step: wire;
    }
    
    transmissions {
        header: until(equals(1));
        body: until(equals(1));
    }
}
```

### Implementation

The general idea is to have the compiler build and maintain the FSM.
I think the design of 

```text
function test() i: packet() => o: packet()
{
    i => {
        // In a connection with a non-trivial datatype, the input will have to "control" the FSM.
        // The question remains, how do we control the output? That is, specify individual transmissions?
        // State transitions should be determined entirely by the data held in the interface.
        // So we could just have the user manually set all of those values.
        // Is there another (maybe better) way?
    } => output
}
```

What about two inputs?
```text
function test() i1: packet(), i2: packet() => o: packet()
{
  i1, i2 => { 
    // Who controls this FSM? i1 or i2?
  }
}
```
A few problems here we'd have to work out.
- There might be some situations where the user wants to process one stream in its entirety, then the other.
  - That seems pretty easy.
- What about one where we want to keep the state in line?
  - This gets a bit complicated.
    - What if two transmissions arrive in the same state at different types? Handling is context dependant.
      - If they can be processed independently, the user will probably write two different circuit statements.
    - What if two states have different numbers of transmissions?
- What if they're different datatypes, so the states don't line up?
  - If we're sending to a function, that's fine. The other function can figure that out.
  - Otherwise, we'd require the user to split this into different circuit statements.

What if we create a function that combines two streams.

```text
function combine() i1: datatype, i2: datatype => o: pair<valid<datatype>, valid<datatype>>
```

### Top-level

I think at the top-level, we'd probably expect to see something like

```text
interface buffer {
    data: byte[8];
    valid: boolean[8];
    last: boolean;
}

datatype byte_steam {
    // Use a reference so that we can use the same interface between data types
    interface buffer
    
    // We would use this notation as a shortcut to defining a transmission group with just one item.
    // So, something like transmission: once would make sense for an int
    transmission: infinite
}

datatype packet {
    interface buffer
    
    transmission {
        header: repeat(1);
        body: until(last, literal(1, 1) => function equals());
    }
}

function main i: byte_stream => o: byte_steam
{
    i => input_packet: function byte_steam_to_packet()
}
```

#### Sending Side

So, I think we can enforce adherence to the interface on the receiving side, since the FSM is essentially built and controlled by the language.
That said, it might be a bit trickier to enforce it on the sending side.
And I think doing so would be necessary for this idea to make sense.
That is, increase safety by having the "datatype" become more explicit.

Would it help to have the designer explicitly mention what "state" their transmission is?
How would we prevent, for example, the designer creating a circuit that meets the "until" condition, then sending another transmission in the same state.

## When is each transmission made?

That's still the part I'm having some trouble with.

How can we allow hardware designers to indicate when a transmission should be made.
And where should these descriptions live?

Does it make the most sense for these descriptions to live in functions, like they do in timeline types?
- What about DSE? What if a function can be implemented in a variety of different ways to allow for it to have different use cases?
  - E.g., I'm sure it's possible to imagine a function that can be pipelined, but at the expense of additional latency for each item.

Some timing information _could_ live on the interface.
- Mainly, the initiation interval.
  - This is something that has to be agreed upon by both the sender and receiver.
  - Although, the sender is permitted to send at a slower rate than the receiver can handle, so it's not exactly like they have to match.

But not all could
- For example, how long it takes a piece of data to traverse a function is something that wouldn't live on an interface, but is important to know.

### Ready / Valid
I think all timing information essentially boils down to figuring out when a function is ready to accept new data and when output data in valid.
Then, making sure that the sender is valid and the receiver is ready before transmissions are made.