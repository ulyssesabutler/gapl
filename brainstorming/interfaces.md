# Interfaces

## Introduction

An interface represents a collection of wires (or, in some instances, a single wire).
These wires can be used as the input and output signals for a function.

For example, let's say we have a function `invert_data`
```text
function invert_data()
    i: data_stream
        =>
            o: data_stream
{ ... }
```

The function has an input `data_stream` interface named `i`, and an output `data_stream` interface named `o`.

Here, we could imagine the `data_stream` interface is defined as follows
```text
interface data_stream
{
    payload: wire[32];
    metadata: wire[32];
    last: wire;
}
```

In this example, each value in this interface (`payload`, `metadata`, and `last`) will become a port in the
    `invert_data` module.
So, when the `invert_data` function is translated to a Verilog module, the resulting module definition will look
    something like
```verilog
module invert_data
(
    // The i interface
    input wire [31:0] i_payload,
    input wire [15:0] i_metadata,
    input wire i_last,
    
    // the o interface
    output wire [31:0] o_payload,
    output wire [15:0] o_metadata,
    output wire o_last
);
...
endmodule
```

## Interface Expressions and Definitions

### Interface Expressions

There are a few different ways an interface can be expressed
```text
InterfaceExpression ::=   WireInterfaceExpression()
                        | DefinedInterfaceExpression(
                            identifier,
                            GenericInterfaceParameterValueList,
                            InterfaceGenericValueParameterValueList,
                          )
                        | VectorInterfaceExpressions(
                            InterfaceExpression,
                            VectorBounds,
                          )
                        | identifier
```

The `wire` interface is the only one defined by the language itself.
Everything else builds on top of it.

Defined interfaces represent the more complex, composite interfaces that are defined either by the user or a library.
These interfaces can be defined generically.
To express that interface, the user will need to provide values for those generic parameters.

Vector interfaces are uniform arrays of a fixed size, where every element is the same interface.

Finally, an interface expression might just be an identifier for a generic interface parameter.
For example, an interface definition might take an interface parameter `T`.
This identifier can then be used anywhere an interface expression would be used.

### Defined Interfaces

There three different ways a user can define an interface.
In all cases, the user must provide an `identifier`.
Any interface definition can also be generic, taking parameters in the definition.

The two types of interface definitions available to users are
- Aliases: An alternative name for another interface expression
- Records: Similar to a struct, a collection of named interfaces. 

```text
InterfaceDefinition ::=   AliasInterfaceDefinition(
                            identifier,
                            GenericInterfaceParameterList,
                            InterfaceGenericValueParameterList,
                            InterfaceExpression
                          )
                        | RecordInterfaceDefinition(
                            identifier,
                            GenericInterfaceParameterList,
                            InterfaceGenericValueParameterList,
                            RecordInterfaceInheritList,
                            RecordInterfacePortList
                          )
```

## Types of Interfaces

### The `wire` Interface

The wire is the "base" of all interface definitions.
On its own, it represents a single wire used as either an input or output port.
For example, consider a function that manipulates a single bit, such as the `not` function.
The definition for such a function might look like

#### Concrete Syntax

A `wire` interface is expressed using its keyword.
```text
function not() i: wire => o: wire { ... }
```
Which will, of course, be translated as
```verilog
module not
(
    input wire i,
    output wire o
);
```

#### Abstract Syntax

A wire is always going to be instantiated with the keyword "wire"

```text
WireInterfaceExpression ::= WireKeyword
```

### Alias Interfaces

Alias interfaces are used to assign a new identifier to another interface.
Aliases can also be used to assign constant values to some interface parameters.

For example, let's say we have an existing interface `data_metadata`.
```text
interface data_metadata(parameter data_size: integer, parameter meta_size: integer) {
    data: wire[data_size];
    meta: wire[meta_size];
}
```

But we want to create a new interface with the metadata always being a single byte.
We can declare an alias interface named `data_metabyte`.
```text
interface data_metabyte(parameter data_size: integer): data_byte(data_size, 8)
```

#### Abstract Syntax
```text
AliasInterface ::= AliasInterfaceDefinition(
                     identifier,
                     GenericInterfaceParameterList,
                     InterfaceGenericValueParameterList,
                     InterfaceExpression
                   )
                   
GenericInterfaceParameterList ::= [identifier[,...]]

InterfaceGenericValueParameterList ::= [InterfaceGenericValueParameter(identifier, StaticSizeExpressionTypeIdentifier)[,...]]
```

_**NOTE**_: For now, we'll limit the `StaticSizeExpressionTypeIdentifier` to just `integer`, to represent vector sizes.
I can't really think of any other use case for interface parameters at the moment.

#### Concrete Syntax

An example of the concrete syntax might be
```text
interface data<T, U>(parameter size1: integer, parameter size2 integer): base_data<T, U>(size1, size2)
```
In this example,
- the identifier is `data`
- the generic interface parameter list is `T, U`
- the generic value parameter list is `parameter size1: integer, parameter size2 integer`
- and the interface expression is `base_data[T, U](size1, size2)`

### Record Interfaces

A record interface is a collection of named sub-interfaces.
To define a record interface, in addition to the standard generic parameters (which are identical to aliases), a user must also provide
- An inherit list
  - A record interface can inherit from another record interface.
    - For example, if we already have a `payload` interface, we can extend it to add the metadata to create `payload_with_metadata`
    - In this case, the port list of `payload_with_metadata` need only contain the metadata fields.
  - This inherit list will be a list of instantiations of previously defined record interfaces
- A port list
  - Finally, the record interface must contain the list of ports
    - In our first example, `data_stream`, we had three ports. `payload`, `metadata`, and `last`.
  - Each port is defined by an identifier, and an instantiated interface
    - Notice that this might include other user-defined interfaces.

#### Abstract Syntax

```text
RecordInterface ::= RecordInterfaceDefinition(
                      identifier,
                      GenericInterfaceParameterList,
                      InterfaceGenericValueParameterList,
                      RecordInterfaceInheritList,
                      RecordInterfacePortList
                    )

RecordInterfaceInheritList ::= [RecordInterfaceExpression(...)[,...]]

RecordInterfacePortList ::= RecordInterfacePort(identifier, InterfaceExpression)[,...]
```

#### Concrete Syntax

```text
interface data_with_metadata<T>(parameter metadata_size: integer): data<T> { metadata: wire[metadata_size]; }
```
In this example, the inherit list is `data<T>`

The port list is `metadata: wire[metadata_size - 1:0];`

#### Translating to Verilog

Verilog doesn't have any support for any kind of composite types.
So, it will be up to the compiler to "expand" records.

Let's say we have the following 
```text
// An alias
interface wire_array(parameter size: integer) wire[size - 1:0]

// A record with a single port
interface last { last: wire; }

// A record with a generic port
interface pair<T> { first: T; second: T; }

// A record that inherits from data
interface pair_stream<T>(parameter metadata_size: integer): last { data: pair<T>; metadata: wire[metadata_size] }

// An alias
interface protocol: pair_stream<wire_array(16)>(8)

function example()
    i: protocol
        =>
            o: wire
{ ... }
```

When we evaluate the `protocol` interface, we'll notice that it has three ports, `data`, `metadata`, and `last`.
We'll also notice that `data` has two sub-interfaces, `first` and `second`.
Both of these are `wire_array`s of size 16, while the metadata is a wire that has 8 bits.

If we want to access the `last` value of `i`, we use `i.last`.
If we want to access `first`, we must do so through `data`, `i.data.first`.

The expanded verilog might then look like
```text
module example
(
    // i
    
    // i.data
    // i.data.first
    input [15:0] i_data_first;
    // i.data.second
    input [15:0] i_data_second;
    
    // i.metadata
    input [7:0] i_metadata;
    
    // i.last
    input last;
    
    
    // o
    output o;
)
```

### Vector Interfaces

A vector interface is an ordered, uniform collection of interfaces.

Vector interfaces can't be defined (although, you can create an alias to a vector interface expression).
To express a vector interface, a user needs to provide the underlying interface being repeated, and array bounds.
The array bounds can be specified by providing an array size.
This creates an array from index 0 to index size - 1.

#### Abstract Syntax

```text
VectorInterfaceExpressions(
  InterfaceExpression,
  VectorBounds,
)
```

#### Concrete Syntax

Let's look at the `pair_stream` example from easier.
Let's say we want to make an array of 10 pair streams with a payload of 16 bit wires and a metadata 8 bits wide.
```text
pair_stream<wire_array(16)>(8)[10]
```

In this example, `pair_stream<wire_array(16)>(8)` is the interface expression that represents the underlying interface.
The vector bounds are specified by `[10]`, which means the array is indexed from 0 to 9.

#### Translating into Verilog

Translating a vector interface into Verilog isn't as straightforward as any of the previous translations we've seen.
One issue is that Verilog does not support multidimensional arrays.

To discuss translating a vector interface expression into Verilog, we'll look at all the possible cases. Those are
- The interface expression is a wire
- The interface expression is an alias
- The interface expression is a record
- The interface expression is another vector

The first case listed here is also the simplest.
If the interface expression is just a wire, we can translate that interface as an input or output port vector from 0 to size - 1.

For example, if we created an alias for a vector interface
```text
interface byte: wire[8]
function invert() i: byte => o: wire { ... }
```

This will be translated to Verilog as
```text
module invert
(
    input [7:0] i,
    output o
);
```

In the case of an alias, we just use the translation rules for the type of interface represented by the underying expression.

For example, if we wanted to translate the following vector interface expression as an input port
```text
byte[10]
```

We want to create a vector of 10 `byte`s, each of which are vectors themselves.
As such, we'd apply the vector translation rule to the `byte` interface (that rule is discussed below).

A record interface consists of a set of named ports.
Translating a vector of record interfaces is equivalent to translating a record of vector interfaces.
So, our translation rule for record interfaces is to simply create a vector of interfaces for each port of the record.
For example, if we have a record interface like so

```text
// Record
interface payload1 { ... }

// Alias
interface payload2: payload1

// Record
interface protocol
{
    cmd: wire[8];
    data1: payload1;
    data2: payload2;
    last: wire;
}
```

Now, we want to translate the following vector interface expression
```text
protocol[10];
```

Our transformation rule says this is the same as translating each port of
```text
interface protocol_vector
{
    cmd: wire[8][10];
    data1: payload1[10];
    data2: payload2[10];
    last: wire[10];
}
```

Specifically, we will expand this record using the record translation rules discussed earlier, where each port is translated as a vector.
Specifically, `cmd` will be translated as a vector of vectors (rule discussed later).
`data1` will be translated as a vector of records.
`data2` will be translated as a vector of aliases.
And, finally, `last` will be translated as a vector of wires.

Finally, how do we translate a vector of other vectors?
Since Verilog doesn't support multidimensional arrays, the compiler will have to manually flatten the arrays.

So, in our previous example,
```text
cmd: wire[8][10]
```
will be translated as
```text
cmd: wire[80]
```

