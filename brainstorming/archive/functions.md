# Functions

A function will be translated into a circuit in Verilog that computes a set of output values based on a set of inputs.
In many cases, a function will be translated as a Verilog module.

## Syntax of Functions

To declare a function, a user needs to provide the following
- An identifier
- The function type
  - This can either be combinational (a stateless function) or sequential (stateful, may take multiple clock cycles).
- The generic interface parameter list
  - Similar to generic interfaces, this is a list of identifiers that represent generic interfaces.
  - These interfaces can be determined or provided when the function is "expressed" or "called"
    - (though, I'm not sure if we want to use the word called in this lang, since there's no call stack)
- The generic parameter list
  - Used for providing other generic values
  - For example, the user might provide a size for a vector, or a default value to initialize a register.
  - See `StaticExpression` in static-expression.md.
- The input list
  - A list of inputs, including the mode of input (more info later), the identifier for the input, and the interface
- The output list
  - Ditto
- Finally, the user will need to provide a list of circuit expressions.
  - We'll cover these in more detail later.

```text
FunctionDeclaration ::= f_d(
                          identifier,
                          FunctionType,
                          GenericInterfaceParameterList,
                          GenericValueParameterList,
                          FunctionInputList,
                          FunctionOutputList,
                          CircuitStatementList,
                        )
        
FunctionType ::= FunctionTypeCombinational | FunctionTypeSequential
FunctionTypeCombinational ::= "combinational"
FunctionTypeSequential ::= "sequential"

GenericInterfaceParameterList ::= [identifier[,...]]

GenericValueParameterList ::= [GenericValueParameter(identifier, StaticExpressionTypeIdentifier)[,...]]
; SHOOT, I FORGOT TO ADD GENERIC FUNCTIONS

FunctionInputList ::= FunctionIO[,...]
FunctionOutputList ::= FunctionIO[,...]
FunctionIO ::= FunctionInputOutput(FunctionIOMode, identifier, InterfaceExpression)
FunctionIOMode ::= FunctionIOModeCombinational | FunctionIOModeSequential
FunctionIOModeCombinational ::= "combinational" ;overloaded
FunctionIOModeSequential ::= "sequential" ;overloaded

...
```

to give an example of a possible concrete function syntax signature

```text
combinational function fold<T>(parameter size: integer, parameter f: function T => T) i: T[size] => o: T { ... }
```
### Circuit Statements

The body of each function is made up of a list of _Circuit Statements_.
These circuit statements come in two forms.
```text
CircuitStatement cs ::= ccs(sbe, cs_l) | ce
```

First, a conditional circuit statement.
```text
ConditionalCircuitStatement ccs(sbe, cs_l)
```

A conditional circuit statement takes two arguments: a static boolean expression and a circuit statement list.
The boolean expression is evaluated, and the circuit statements are included only if the expression is true.

Second, they come in the form of circuit expressions.
```text
CircuitExpressionInterfaceDefinition ce_int_d(identifier, InterfaceExpression)
CircuitExpressionInterfaceExpression ce_int_e ::= ce_int_d | identifier ;of previously defined interface
CircuitExpressionInterfaceListExpression ce_int_l_e ::= ce_int_e[,...]

CircuitExpressionFunctionDefinition ce_f_d(identifier, FunctionExpression)
CircuitExpressionFunctionExpression ce_f_e ::= ce_f_d | identifier

CircuitExpressionConsumer ce_consumer ::= ce_f_e | ce_int_l_e

CircuitExpressionRecordInterfaceConstructor ce_int_c(csl) ;this syntax isn't particularly insightful at the moment
                                                          ;but the basic idea is that we want to create syntactic sugar
                                                          ;for expressing a circuit with non-trivial circuits used to
                                                          ;compute the value for each sub-interface.

CircuitExpressionProducer ce_producer ::= ce | ce_consumer | ce_int_c

CircuitExpression ce ::= ce_int_d | ce_f_d | CircuitExpressionConnection(ce_producer, ce_consumer)
```

Let's unravel this while we explain the proposed concrete syntax.
A circuit expression can either be a declaration, or a connection.
```text
f: add; // Declaring an add function
a: u32int; // Declaring an interface
b: u32int;

a, b            // A interface list expression
  => f          // f is a function expression. The "=>" creates a connection with (a,b) as the producer and (f) as the consumer
  => c: u32int; // A declaration acting as an interface expression. (a,b=>f) is the producer, and (c) is the producer

// It's also worth clarifying that an identifier can be for either, in the case of a function, a CircuitExpressionFunctionDefinition, or just a FunctionDefinition.
a, c => add => d: u32int;
```
Now, let's say we have the interface
```text
interface example
{
  payload: u32int;
  metadata: wire;
}
```
We can express this using the record interface constructor.
```text
{
  a, b, c => complicated_fun => payload;
  
  if (condition) {
    0 => metadata;
  } else {
    1 => metadata;
  }
} => x: example; // send the result to an interface named x
```

## Semantics

When developing an RTL description of a circuit, engineers often think in terms of combinational and sequential logic.
Combinational logic is the lower-level logic, where we combine values using simple gates, such as AND, OR, and NOT gates.
Combinational logic should be stateless without memory components like latches or flip-flops.

Sequential logic, by contrast, is stateful.
A sequential circuit will usually have a memory.
For example, it might have a set of registers.
These registers have an input and output.
The register will maintain the same output value throughout the duration of a clock-cycle, regardless of changes to its
  input.
At the beginning of the clock cycle, the register will "capture" the current input value and use that as its output
  value during the next clock cycle.

### Combinational Functions

We'll start by exploring combinational functions in a bit more depth.
Not only are they simpler, but since every sequential function uses combinational logic, combinational functions are
  essentially a prerequisite.

The primary requirement of every function is that each consumer is connected to exactly one producer.
Initially, the only consumers will be the output of the function.
That said, as we declare other functions and interface, we're creating additional consumers.
```text
function addition() a: u32int, b: u32int => c: u32int
{
    adder_input_1: u32int; // Creating two consumers in the form of interface delcarations
    adder_input_2: u32int;
    
    adder: add => c; // The function output (c) is not satisfied, but we created another consumer in the process
    
    adder_input_1, adder_input_2 => adder; // The adder is now satisfied.
    
    a, b => adder_input_1, adder_input_2; // The two interfaces are now satisfied
}
```

Combination functions are also simple since they're translated generally how you'd expect.
The inputs and outputs of a function are translated to a modules input and output ports fairly directly (using the translation rules discussed in interfaces.md).
No additional overhead is required to manage communication between two modules.

***NOTE***: _While no additional overhead is required, we might later explore the possibility of automatically pipelining certain modules.
This could help offload the burden of tweaking a design to meet timing from the engineer to the compiler.
To accomplish this, we would need to add additional constructs that would allow the user to specify performance constraints._

So, as an example, if we have two functions with signatures like
```text
combinational function transformation1() i: u32int => o: u32int { ... }
combinational function transformation2() i: u32int => o: u32int { ... }
```
And we use them as such
```text
combinational function composition() i: u32int => o: u32int
{
  i => transformation1 => transformation2 => o;
}
```

And we can reasonably expect the result to be the creation of three modules,
  with the first two chained together in the last.
```text
module composition
(
  input [31:0] i,
  output [31:0] o,
)
{
  wire [31:0] t1_o;
  transformation1 t1
  (
    .i(i),
    .o(t1_o)
  );
  
  transformation2 t2
  (
    .i(t1_o),
    .o(o)
  );
}
```

One of our sources (I can't remember which) mentioned that a big part of the reason why Verilog programs aren't broken
  down into smaller functions is because of how much overhead is associated with instantiating a new function.

## Sequential Functions

The sequential function model
```text
Producer        Module interior           Consumer
         |------------------------------|
data  -> |                              | ->  data
valid -> |                              | -> valid
         |                              |
ready <- |                              | <- ready
         |------------------------------|
```

What values do we need to set?
- `consumer.data`, the output of the module
- `consumer.valid`, we need to tell the consuming module when we provide data for it to read
- `producer.ready`, we need to tell the previous module when it can transmit data to us.

What values do we use?
- `producer.data`, to compute the output
- `producer.valid` and `consumer.ready`, are used to trigger the transfer event.

The interfaces of sequential functions, in addition to carrying data, also have ready and valid contracts.
For now, we'll concentrate on latency-insensitive interfaces, where a value is considered ready when the accompanying
  valid wire is set.
The individual components of each interface should be accessible (i.e., `data`, `valid`, and `ready`).

Recall from earlier that each input and output also has a mode.
That mode indicates whether the input or output is a combinational input that has data flowing in one direction which is
  assumed to always be valid and the receiver always assumed ready, or a sequential input that allows the engineer to
  indicate when they're module is ready to receive data and when the data they're sending is valid.

By default, all inputs and outputs are assumed to have a mode that reflects the function type.
In sequential functions, I think this constraint can be enforced
  (I can't really think of a reason why you'd want to change the input to a combinational input in the middle of a clock cycle).
That said, there might be times when we want to create a combinational circuit that uses and sets the ready and valid values
  (for example, the duplicator and input arbiter are both combinational circuits, or at least, can be).

Modes aren't just used to describe the input and output of a function.
They can also be applied to circuit expression interfaces.
Clearly, the modes of any two interfaces must match before a connection can be made.
Take the following example
```text
combinational function stage1() input_stream: stream => output_stream: stream { ... }

combinational function stage2()
  sequential input_stream: stream,
  enable: boolean // combinational by default
    =>
      sequential output_stream: stream
{
  ...
}
```
- In `stage2`, `input_stream => output_stream` is the equivalent to
  - `input_stream.data => output_steam.data`
  - `input_stream.valid => output_steam.valid`
  - `output_stream.ready => input_stream.ready`
- If, in `stage2`, we attempted to connected `input_stream => stage1 => ...`, we would get an error
  - Specifically, we're attempting to connect a sequential interface to a combinational interface
  - Instead, we should connect `input_stream.data => stage1 => output_stream.data`.
  - Note that, unlike the first example, this leaves `input_stream.ready` and `output_stream.valid` unsatisfied.

## Verilog Translations

Part of the hope with this project is that the compiler could do perform the tedious work of determining how to
  connect two different modules.