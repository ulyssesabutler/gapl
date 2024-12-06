# NetFPGA Packet Processor

## Standard Library

First, we'll start with a few standard defintions
```text
interface bit wire
interface boolean wire
interface byte bit[7:0]

interface bits(parameter count: integer) bit[count - 1:0]
interface booleans(parameter count: integer) boolean[count - 1:0]
interface bytes(parameter count: integer) byte[count - 1:0]

interface index(parameter container_size: integer) bit[$clog2(container_size) - 1:0]
```

## NetFPGA Interfaces

Next, we'll create the interfaces the help us transmit the packet streams using the AXI-Stream protocol.
This is loosely based on the ShakeFlow interface definitions.

```text
interface data_with_keep(parameter byte_count: integer)
{
    payload: bytes(byte_count);
    keep: booleans(byte_count);
}

interface user_data(parameter byte_count: integer)
{
    user: bytes(byte_count);
}

interface data_stream(parameter type: interface)
{
    data: type;
    last: boolean;
}

interface axis
(
    parameter data_byte_count: integer,
    parameter user_byte_count: integer,
): data_stream(data_with_keep(data_byte_count)), user_data(user_data_byte_count)
```

## Helper functions

### `axis_arbiter`
This module takes two AXI streams as an input and merges them into a single stream.
This is done in a round-robin fashion, reading the entirety of one stream before switching to the other.
This is implemented using a single register (which tracks the current input stream).
That register is an input to a multiplexer, which hooks the current input to the output.

```text
sequential function axis_arbiter(parameter type: interface)
    input0: axis(type),
    input1: axis(type),
        =>
            output: axis(type)
{
    /* Create a register that tracks which of the two current
     */
    current_input_selection: register(wire, 0);

    /* On this line, we're creating a multiplexer. A multiplexer (or, mux) is a combinational circuit. This specific mux
     * is used for streams (see the document about the connector).
     *
     * Why do we need to pass the predicate and value of each case into a separate function (i.e., the stream_case)? I
     * don't think it makes a lot of sense for the individual properties of interfaces to be marked as streams, since
     * they're more analogous to structs. If we break from the model of each input and each output having, at most, one
     * ready and valid contract, that might start to get pretty complicated.
     *
     * Instead, we're going to feed both the predicate and the value into the stream_case function. Essentially, all
     * this function does is connect the ready and valid wires of the input value to the output, which basically ignores
     * the predicate (assuming it's always valid and never signalling to it whether or not it's ready).
     *
     * Specifically, the stream_multiplexer will pass through the valid value of the current selection, and pass the
     * ready value back to that stream. The other stream will receive a ready value of false.
     *
     * So, even though the mux is not a sequential function, it still carries the ready and valid signals.
     *
     * One final note, in this specific instance, the stream_multiplexer that's being created is anonymous, and the
     * current_input is a stream that's being declared and assigned with an implicit type (we could also name the
     * stream_multiplexer to the same effect). Finally, the result of that is connected to the output.
     */
    (
        (current_input_selection, 0 => equal),
        input0 => stream_case
    ),
    (
        (current_input_selection, 1 => equal),
        input1 => stream_case
    ) => stream_multiplexer => current_input => output;

    /* The above code hooks up the data flow (inputs to outputs, along with all the ready and valid signals). That said,
     * we still need to add the logic
     * I still need to think up some "syntactic sugar" for "if valid and ready, do this".
     * For now, we'll just do it "manually".
     */
    current_input.valid, output.ready => has_transmission_occurred;
    has_transmission_occurred, current_input.last => should_switch;

    {
        should_switch                       => if;
        current_input_selection, 1 => add   => then;
        current_input_selection             => else;
    } => priority => current_input_selection;
}
```

### `axis_duplicate`

This is essentially the inverse of the `axis_arbiter`.
Instead of taking multiple inputs and combining them into a single output, we want to take a single input and split it
    into multiple outputs.

```text
sequential function duplicate(parameter type: interface)
    input: type
        =>
            output1: type,
            output2: type,
{
    input.interface => output1.interface;
    input.interface => output2.interface;

    output1.ready, output2.ready => and => input.ready;

    /* This part is interesting. First, it's obvious why the the input data has to be valid for the output data to be
     * valid. That said, the validity of the output is also dependent on the readiness of both output. Essentially, we
     * want to prevent one side from transmitting before the other side transmits.
     *
     * The compiler will notice this dependency and mark this function as a "demanding" (as opposed to "helpful")
     * producer. If either of the consumers of this modules outputs are demanding consumers, the compiler will know to
     * automatically add a FIFO queue.
     */
    output1.ready, output2.ready, input.valid => and => output_valid;

    output_valid => output1.valid;
    output_valid => output2.valid;
}
```

### `generate_mask`

```text
combinational function generate_mask(parameter mask_bit_size: integer)
    start_index: index(mask_bit_size),
    end_index: index(mask_bit_size),
        =>
            mask: bits(mask_bit_size)
{
    (1, (end_index, 1 => add) => left_shift), 1 => subtract => beginning;
    (1, start_index => left_shift), 1 => subtract => bitwise_not => ending;
    
    beginning, ending => bitwise_and => mask;
}
```

### `sink`

This is basically a function that will continuously read the input, always asserting the ready signal

```text
sequential function sink(parameter type: interface) stream input: type => null { true => input.ready; }
```

### `count_transmissions`

Probe the data as it passes through the module to count the number of transmissions this module has processed

```text
sequential function count_transmissions(parameter type: interface)
    stream input: type,
    interface reset: boolean
        => 
            stream output: type,
            interface count: u32int
{
    input => output;
    
    current_count register(u32int, 0) => count;
    
    {
        [
            { reset => if; 0 => then; }
            { input.valid, output.ready => and => if; current_count, 1 => add => then; }
        ] => ifs;
        current_count => else;
    } => priority => current_count;
}
```

## Network Processing Functions

### `axis_parser`

This function takes an axis stream as an input.
It's also configured with a starting index and a size.
It outputs a bit array with the bit values starting from the start index.
The function then hold that value until the function is manually reset.

```text
combinational function axis_parser_indices_for_transmission
(
    parameter data_size: integer,
    parameter start_index: integer,
    parameter end_index: integer,
)
    bits_read_in_stream: u32int
        =>
            bits_in_current_transmission: boolean,
            start_index: u32int,
            end_index: u32int,
{
    {
        start_index, bits_read_in_stream => greater_than => if;
        start_index, bits_read_in_stream => subtract => then;
        0 => else;
    } => priority => start_index_in_transmission;
        
    {
        (end_index, bits_read_in_stream => subtract), data_size => less_than => if;
        end_index, bits_read_in_stream => subtract => then;
        (data_size - 1) => else;
    } => priority => end_index_in_transmission;
    
    {
        ((end_index, 1 => add), bits_read_in_stream => greater_than_or_equal),
        (start_index, (bits_read_in_stream, data_size => add) => less_than_or_equal)
            => if;
            
        end_index_in_transmission, start_index_in_transmission => subtract
            => then;
        
        0   => else;
    } => priority => bits_read_in_transmission
}
```

```text
sequential function axis_parser
(
    parameter axis_data_byte_count: integer,
    parameter axis_user_byte_count: integer,
    parameter start_index: integer,
    parameter end_index: integer,
)
    // This will have a ready and valid wire
    stream input: axis(axis_data_byte_count, axis_user_byte_count),
    // This will not. It's just a combinational input signal
    interface reset: boolean
        =>
            // This will have a valid signal, though its ready signal won't be used
            stream output: bits(end_index - start_index)
{
    // 1. We need to track how many bits have been read during this stream
    input, reset => count_transmissions => sink, bytes_read_in_stream;
    bytes_read_in_stream, 8 => multiply => bits_read_in_stream;
    
    // 2. Find the output indices and number of bits in the current transmission
    bits_read_in_stream
        => axis_parser_indices_for_transmission(axis_data_byte_count * 8, start_index, end_index)
        => bits_read_in_current_transmission, start_index_in_current_transmission, end_index_in_current_transmission;
    
    // 4. Generate the input mask to fetch the data that will be used in the output
    {
        bits_read_in_transmission => nonzero => if;
        start_index_in_transmission, end_index_in_transmission => generate_mask(axis_data_byte_count * 8) => then;
        0 => else;
    } => priority => input_to_output_mask;
    
    (input.interface, input_to_output_mask => bitwise_and),
    start_index_in_transmission
        => right_shift => parsed_data_from_input;
    
    // 5. Compute number of bits written to the output
    bits_written_to_output register(u32int, 0), (axis_data_byte_count * 8) => equals => output.valid;
    
    {
        [
            { reset => if; 0 => then; }
            { input.valid => if; bits_written_to_output, bits_read_in_current_transmission => add => then;
        ] => ifs;
        bits_written_to_output => else;
    } => priority => bits_written_to_output;
    
    // 6. Compute the output
    output_buffer register(bits(end_index - start_index), 0) => output;
    
    {
        [
            { reset => if; 0 => then; }
            { input.valid => if; output_buffer, (parsed_data_from_input, bits_written_to_output => left_shift) => bitwise_or => then; }
        ] => ifs;
        output_buffer => else;
    } => priority => output_buffer;
}
```