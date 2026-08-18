# FPGA Language

## Flex

This is the lower-level option.
There's a pretty clear connection between this hardware and the underlying Verilog code.

### Types
- Wires
  - Can have widths
  - Can be signed or unsigned
  - E.g., `x: unsigned wire[5:0];`
- Modules
```
module (
  parameter size: integer;
  input ready_valid: ready_valid(size);
)
```
- Parameters
  - Static values, known at compile time
  - Datatype can include integer, string?
  - E.g., `parameter size: integer;`

### Interfaces
```
interface data (parameter size: integer) {
  forward data: unsigned wire [size - 1:0]; // The lowest level
}

interface data_valid (parameter size: integer) : data(size) { // This is an extention
  forward valid: wire;
}

interface ready_valid <SIZE> {
  forward  data:  data_valid<SIZE>; // This is a nested interface
  backward ready: wire;
}
```
An interface represents a collection of wires.
These wires have names and types associated with them.
These types can be wires at the lowest level, or other interfaces.

We can also add constraints onto the values in these interfaces.
In the examples above, these are just directions.

## Automatic Interfaces

I wonder if we instead get the compiler to generate the interfaces instead.
So, interfaces wouldn't have directions at all.
Instead, each module would just have a single input interface, and a single output interface.
Each module is then responsible for specifying the logic that details when it is ready to receive the input.
It also specifies when the output is considered valid.

Types of contracts:

I wonder if we could make this general enough that all existing contracts can be easily defined
  (helpful/demanding, ready/valid, valid/credit, valid yumi, synchronous/latency-sensitive, etc.).
And, once the user defines the ready/valid logic, can we make the compiler smart enough to determine the necessary interface?

Can the compiler automatically determine when a latency-insensitive interface is necessary?
Can it determine when a latency-sensitive, synchronous interface is appropriate?
If it does create a latency-sensitive interface, can it determine whether or not the timing is met (similar to timeline types)?

***NOTE***: For performance critical components, we might consider allowing some way to "restrict" how inefficient the compiler can make a submodule.
This might allow the compiler the flexibility to add latency-insensitive interfaces in some situations to resolve conflicts.
But it would also allow the designer to receive an error whenever their interfaces conflict.

Can the compiler determine whether an interface is helpful or demanding?
And if so, can it automatically insert a FIFO when necessary?

Types of input and output

The simplest example is going to be a single stream as an input and a single stream as an output.
In this case, the implicit ready/valid is sufficient.

### Interfaces

Interfaces can be predefined or "anonymous." They are analogous to structs in other languages.

```
interface data(size: integer) {
  data: unsigned wire[size - 1:0];
}

interface data_with_meta(
  data_size: integer,
  meta_size: integer,
): data(data_size) {                              // extention. Alternatively, data(size: data_size)
  meta: unsigned wire[meta_size - 1:0];
}

interface three_types_of_data(
  data1_size: integer,
  meta1_size: integer,
  
  data2_size: integer,
  meta2_size: integer,
  
  data3_interface: interface,           // generic
) {
  data1: data_with_meta(data1_size, meta1_size);  // nested
  data2: data_with_meta(data2_size, meta2_size);
  data3: data3_interface[7:0];                    // Create an array of 8 "data3_interfaces"
}
```

We should probably also make interfaces that are "ordered". That is, arrays instead of structs.

```
interface byte wire[7:0];
interface pair(type: interface) type[1:0];
```

In all cases, these interfaces will represent data moving in a single direction (input to output).
All the data in a given interface will be ready or valid at the same time.

That said, there are situations where different pieces of data will need to have different ready and valid signals.
For example, consider an arbiter, which will need to handle each valid signal independently.
Or, on the flip side, a duplicator, which will need to handle each ready signal independently.

### Functions / Modules

Each function needs a few things.
- Name: an identifier that can be used to instantiate it
- Parameters: might be static values (integers), interfaces (used to declare I/O), functions (for higher-order functions, to specify structure)
- Type: specifying the input and output interface.
  - Probably using the same notation as "driving values"
  - We'll also probably want a way to "pattern match" to assign the input values names
  - We could also create reserved words, `input` and `output`.
    - `input` could be a keyword representing the previous function. So, it might have `input.valid` and `input.output`.
    - Similarly, output would have an `output.ready`, and we could drive to `output.input` and `output.valid` directly.
  - We probably can't have each module have exactly one input and one output.
- A ready contract (SEQUENTIAL ONLY)
  - Possible contract: boolean wire
  - We could create a reserved word called `ready`
    - Then again, this might be confusing, leading users to believe that only valid/ready interfaces are supported
  - Possible contract: timeline types
    - We could create a pseudo-function called something like `timer`
    - `function timer(time: integer) wire => wire // true after time clock cycles`
    - `input.valid => timer(5) => ready;`
      - Once we get a piece of data, we'll be ready for the next piece of data in 5 clock cycles.
    - `valid => ready;`
      - No pipelining, we can accept a new piece of data once the current data has been output
  - Possible contract: credits
    - `input.valid => timer(5) => credit(max = 3) => ready;`
    - Would something like this work?
    - Are there any extra features we would need to allow for on the producer side?
  - Will the compiler be able to verify that that contract is fulfilled with nested modules?
- A valid contract (SEQUENTIAL ONLY)
  - One possible contract would be on the value of a boolean wire
  - We could create a reserved word called `valid`
    - Then again, this might be confusing, leading users to believe that only valid/ready interfaces are supported
  - One other possible contract would be timeline types
    - We could use the `timer` function again
    - `input.valid => timer(5) => valid;`
    - The output is valid 5 clock cycles after our input is valid
    - This leaves the problem of how to indicate pipelining. Could the compiler infer it from the ready contract?
  - Will the compiler be able to verify that that contract is fulfilled with nested modules?

#### Function Format
The definition of a function looks as follows
```text
[sequential|combinational] function FUNCTION_NAME([PARAMETER[,...]]) INPUT[,...] => OUTPUT[,...] { DEFINITION }
```

- `[sequential|combinational]`
  - These allow us to build an explicit hierarchy
  - `combinational` functions can only call other combination functions
    - That is, a `combinational` function cannot contain a `sequential` function (such as a `register`).
  - `sequential` functions require users to explicitly specify `ready` and `valid` contracts.
  - The `ready` and `valid` contracts in a `combinational` function are always just `true`.
    - ***NOTE***: This may be a bit simplistic. Instead, a combination circuit might just "pass through" the ready and valid signals
      - That would require the user to manually specify them if there are multiple inputs or outputs, which I think is what we want.

- Parameter list
  - Each parameter is formatted as `PARAMETER_NAME: TYPE`
    - E.g., `size: integer`, `predicate: function type => boolean`
    - ***NOTE***: This means we could have comas in the type name, potentially making this difficult to parse
      - As a possible solution, could surround the type in parentheses if it's a parameter list.
      - For example, we could do `var` and `val` for what verilog calls `parameter` and `localparam`?

- Inputs and outputs
  - Should all inputs and outputs be streams?
    - If a non-stream is passed to a module, should it be upgraded to a stream?

#### Connecting Modules

`=>`: The connector operator

`module => interface`
- For a module of type 

Cases

Registers can be functions, with a single parameter
- That single parameter is the interface, used for both input and output
- Both valid and ready are always true.
```
sequential function register(type: interface, initial: ???) type => type
{
  input => native => output; // native could be a psuedo-function here, similar to native function in JVM.
  true => valid;
  true => ready;
}
```

Priority routers can be functions
```
interface predicate(output_type: interface) {
  if: any; // Question: How do we indicate that this can accept any value?
  then: output_type;
}

interface predicate_with_default(output_type: interface) predicate(output_type) {
  else: output_type;
}

function if(output_type: interface) predicate_with_default(output_type) => output_type
{
  input => native => output;
}

interface predicate_list(
  size: integer,
  output_type: interface,
): predicate(output_type)[size - 1:0]

interface predicate_list_and_default( // "and" instead of "with" to represent nesting instead of extention
  size: integer,
  output_type: interface,
) {
  if: priority_list(size, output_type);
  else: output_type;
}

function priority(
  size: integer,
  output_type: interface,
) predicate_list_and_default(size, output_type) => output_type
{
  {
    [size, 1] => new greater_than => if; // Question, how do we disambiguate interface names from function calls?
    
    {
      input.if => new tail => if;
      input.else => else;
    } => new priority(size - 1, output_type) => then;
    
    {
      input.if[0].if => if;
      input.if[0].then => then;
      input.else => else;
    } => new if => else,
  } => new if => output;
}
```

Questions
- How do handle the clock? Is it always visible? Does it need to be visible?
- How do we indicate that a type can be any value? (e.g., `if: any;`, `equal` function)
- How do we infer parameters? (e.g., the output type and list size of the priority queue?)
- Optional: Should we specify functional as combinational or sequential?
  - If they're combinational, `ready` and `valid` are unnecessary, since they'll both always be true.
  - The default should be something in between. If we are able, 
- How do we disambiguate interface names from function calls?
- What's the input type to register's initial value
- Syntactic sugar for "if valid and ready, do this"
  - For this block, we probably need to specify the input and output interfaces.
  - We'll also need to specify the "default values"
    - That is, if we're not valid and ready, the registers should either, maintain their current value.
      Or, they should maintain some default value, like 0.
  - Finally, we'll need to specify the new values
  - We'll probably want to have some "event-based" system, similar to timeline types.