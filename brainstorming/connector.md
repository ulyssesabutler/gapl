# The Connector, `=>`

This will only ever be used in the context of a function.
- We could imagine a function as an operation that transforms one (or more) interfaces into one (or more) interfaces.

## Hierarchy

We could imagine a hierarchy of different "types" this connector can work with

### Streams
- These represent a bundle containing an interface, a ready, and a valid contract
  - ***NOTE***: Should interfaces have the same access operator as interfaces?
  - The only three possible values are `interface`, `valid`, and `ready`
- For example, the inputs and outputs of a function would both be streams

So, connecting a stream to something else would evaluate to a new stream

```text
function example(parameter type: interface) input: type => output: type // input and output are both streams
{
    // input and output are both streams

    input => tmp: type; // tmp an interface of type. It has an (optional?) type hint This evaluates to another stream.
    
    tmp => map => output; // map is another function of type => type. So, (tmp => map) evaluates to another stream, which is an operand to the next => operator
}
```

So, in the most simple case of
```text
function example(parameter type: interface) input: type => output: type
{
    // 1. At this point, you could imagine the interface, ready, and valid wires of input and output as unconnected
    input => output;
    // 2. Now, the interface and valid wires of input have been hooked to output, and the ready wire of output to input.
}
```

In the case of multiple inputs
```text
function example(parameter type: interface) input1: type, input2: type => output: type
{
    // 1. At this point, no connections are made.
    input1 => output;
    // 2. Now, input1 and input2 are fully connected, but input2's ready wire has no driver (an error)
    
    false => input2.ready;
    // 3. This resolve all of the errors. Although, it's worth noting that this expression does not evaluate to a stream.
}
```
```text
function example(parameter type: interface) input1: type, input2: type => output: type
{
    input1 => output;
    input2 => output; // This is obviously an error, since we have multiple drivers for output.interface and output.valid
}
```

So, it looks like connecting a stream to something else promotes that thing to a stream.
- `input => output;` Both input and output were already streams
- `input => tmp;` This creates an identifier, `tmp`, which is a stream with `tmp.interface == input.interface`.
- `input => tmp: type;` Same as the above, but a type hint is provided. Maybe this can be used for casting?
- `input => map;` Here, map is a function, so this evaluates to a stream, but with no identifier.
  Thus, the stream is actually left hanging.
- `input => map => output;` If we actually want to use the stream created above, we can immediately connect it.
- `input => transformed_stream: map;` Or, we can give it an identifier.
- `transformed_stream => output;` That identifier then represents a new stream.

### Interfaces

Normally, everything inside an interface is also an interface. For example, in
```text
interface boolean wire;

interface axis
{
    byte: wire[8];
    is_null: boolean;
}
```
- `wire` can be considered the "base" interface
- `boolean` is essentially another name for `wire`.
- `axis` is an interface we define.
- `axis.byte` is also an interface. (at least in the context of this hierarchy, evaluating the connector)
- `axis.is_null` is also an interface.

Each stream also has an accessible interface
```text
function f(parameter type: interface) in: type => out: type
{ 
    // Accessing the interfaces directly, so we don't connect the streams
    in.interface => out.interface; // Unlike before, this just evaluates to an interface
    
    // Hook up the remainder of the stream manually
    in.valid => out.valid;
    out.ready => in.ready;
}
```