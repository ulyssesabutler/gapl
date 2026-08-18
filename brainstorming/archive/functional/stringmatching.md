# String Matching

The goals is to create an algorithm that works roughly as follows

```
needle:
DATA

heystack:
THISISADATEDEXAMPLEOFPACKETDATABEINGSEARCHED
           previous
              current
                 reason
         D 0  0  since D != T
         A 1  0  since A != T
         T 0  1  since T == T, and the previous value from the above was 1
         A 0  0  since A != T
```

We want to split this into two different parts.
First, a (likely pipelined) string equality checker.
Second, we will need a stream searching function.

We're going to try and build these using collection combinators. So far, we're using
- `zip()`
- `map(operation)`
- `fold(initial, operation)`
- `windowed(size, step)`

## GAPL

First, the string equality checker
```
function pipelined_string_equality
(
    parameter length: integer, // Both strings need to be of a fixed length
):
    candidate: string(length), // Changes each clock cycle
    reference: string(length), // Stays the same
        =>
            result: boolean,
{
    candidate, reference => zip()
        => map(operation = character::equals())
        => fold(initial = true, operation = boolean::and())
        => result;
}
```

Then, the stream searcher
```
function search_stream
(
    parameter length: integer,
):
    needle: string(length), // There's an implicit assumption here that this value won't change.
    heystack: stream<character>,
        =>
            result: boolean,
{
    heystack => windowed(size = length, step = 1)
        => map(operation = pipelined_string_equality(length))
        => fold(initial = false, operation = boolean::or())
        => result;
}
```

## Implementation Ideas

Kotlin's Sequences can be the source of some inspiration here:
https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.sequences/-sequence/

Some functions, like `windowed` and `zip` in the previous example, or `chunked`, `take`, and `drop`, can be implemented using routing alone.

The `pipelined_string_equality` function is interesting.
At a high level, we should be able to feed it a new string each clock cycle, and it should output the result of the equality check after `reference.length` cycles.
The obvious way to implement this module, generally, is to create a pipeline with registers between each stage that store the candidate strings.

This is, in fact, probably necessary if we use this function to do something like
```
stream => chunked(size)
    => map(operation = pipelined_string_equality(length))
    => ...
```
where each string is independent.

But we'll notice that, in our specific case of the sliding window, this wastes some FFs, since, as mentioned, `windowed` can be implemented using routing.

Maybe the `windowed` function could handle the tracking of the pipeline stages, while the actual string searching module would just do the comparisons

## Verilog

First, a very simple implementation of the `windowed` module, with a window size of 3.
It should be noted that this probably isn't optimal, the `buffer` wastes a lot of FFs.
That said, it's easier to understand the edge cases here.
E.g., when the window crosses the boundary of two transmissions.

Also, this doesn't work if there's a transmission that isn't "ready" when we need it.
But I didn't realize that until I wrote this, and I'm sort of glossing over it.

```verilog
module windowed_3
(
    // Global
    input clock,
    input reset,

    // an AXI-stream input, 32 bytes at a time
    input [255:0] data,
    input valid,
    output ready,
    
    // Output windows
    output [23:0] window_0,
    output [23:0] window_1,
    output [23:0] window_2,
);

    reg ready_reg;
    reg ready_reg_next;

    reg [255:0] buffer;
    reg [5:0] current_buffer_index;
    
    reg [31:0] buffer_next;
    reg [1:0] current_buffer_index_next;
    
    reg [7:0] window_0_reg;
    reg [7:0] window_1_reg;
    reg [7:0] window_2_reg;
    reg [7:0] window_3_reg;
    reg [7:0] window_4_reg;
    
    reg [7:0] window_0_reg_next;
    reg [7:0] window_1_reg_next;
    reg [7:0] window_2_reg_next;
    reg [7:0] window_3_reg_next;
    reg [7:0] window_4_reg_next;
    
    assign window_0 = {window_0_reg, window_1_reg, window_2_reg};
    assign window_1 = {window_1_reg, window_2_reg, window_3_reg};
    assign window_2 = {window_2_reg, window_3_reg, window_4_reg};
    
    // First, assign the output window values
    always @(*) begin
        window_1_reg_next = window_0_reg;
        window_2_reg_next = window_1_reg;
        window_3_reg_next = window_2_reg;
        window_4_reg_next = window_3_reg;
        
        window_0_reg_next = buffer >> (current_buffer_index * 8);
    end
    
    // Next, refil the buffer if needed
    // Notice we're assuming valid is always true, cuz I got lazy
    always @(*) begin
        if (current_buffer_index != 31) begin
            ready_reg_next = 0;
            current_buffer_index_next = current_buffer_index + 1;
            buffer_next = buffer;
        end else begin
            ready_reg_next = 1;
            current_buffer_index_next = 0;
            buffer_next = data;
        end
    end
    
    always @(posedge clock) begin
        if (reset)
            ready_reg <= 0;
        
            buffer <= 0;
            current_buffer_index <= 0;
            
            window_0_reg <= 0;
            window_1_reg <= 0;
            window_2_reg <= 0;
            window_3_reg <= 0;
            window_4_reg <= 0;
        end else begin
            ready_reg <= ready_reg_next;
            
            buffer <= buffer_next;
            current_buffer_index <= current_buffer_index_next;
            
            window_0_reg <= window_0_reg_next;
            window_1_reg <= window_1_reg_next;
            window_2_reg <= window_2_reg_next;
            window_3_reg <= window_3_reg_next;
            window_4_reg <= window_4_reg_next;
        end
    end
    
endmodule
```

Now, the implementation of the `pipelined_string_equality` module with a length of 3
```verilog
module pipelined_string_equality_3
(
    // Global
    input clock,
    input reset,

    // Pipeline stages
    input [23:0] candidate_0,
    input [23:0] candidate_1,
    input [23:0] candidate_2,
    
    // Reference string
    input [23:0] reference,
    
    // Output
    output result,
);

    reg  candidate_0_result;
    wire candidate_0_result_next = (candidate_0[7:0] == reference[7:0]);
    
    reg  candidate_1_result;
    wire candidate_1_result_next = (candidate_1[15:8] == reference[15:8]) & candidate_0_result;

    reg  candidate_2_result;
    wire candidate_2_result_next = (candidate_2[23:16] == reference[23:16]) & candidate_1_result;
    
    assign result = candidate_2_result;
    
    always @(posedge clock) begin
        if (reset) begin
            candidate_0_result <= false;
            candidate_1_result <= false;
            candidate_2_result <= false;
        end else begin
            candidate_0_result <= candidate_0_result_next;
            candidate_1_result <= candidate_1_result_next;
            candidate_2_result <= candidate_2_result_next;
        end
    end
    
endmodule
```

Finally, let's implement fold before tying them all together
```
module fold
(
    // Global
    input clock,
    input reset,
    
    input current_input, // boolean, remember, we're folding booleans on or
    
    output current_output, // At the last clock cycle, this will also contain the correct value
);

    reg current_output_reg;
    
    // This is where we implement "operation." In this case, "boolean or"
    wire current_output_reg_next = current_output_reg | current_input;
    
    always @(posedge clock) begin
        if (reset) begin
            current_output_reg = 0;
        end else begin
            current_output_reg = current_output_reg_next;
        end
    end

endmodule
```

Finally, let's tie these all together.
```
module search_stream
(
    // Global
    input clock,
    input reset,

    // an AXI-stream input, 32 bytes at a time
    input [255:0] data,
    input valid,
    output ready,
    
    // Output
    output result
);

    wire [23:0] window_0;
    wire [23:0] window_1;
    wire [23:0] window_2;

    windowed_3
    (
        .clock(clock),
        .reset(reset),
        .data(data),
        .valid(valid),
        .ready(ready),
        .window_0(window_0),
        .window_1(window_1),
        .window_2(window_2),
    );

    wire string_equality_result;

    pipelined_string_equality_3
    (
        .clock(clock),
        .reset(reset),
        .candidate_0(window_0),
        .candidate_1(window_1),
        .candidate_2(window_2),
        .reference("cat"), // Damn, this isn't really accounted for in the GAPL psuedo code
        .result(string_equality_result),
    );

    fold
    (
        .clock(clock),
        .reset(reset),
        .current_input(string_equality_result),
        .current_output(result),
    );

endmodule
```

This just leaves the question of how we build a compiler that spits out something functionally identical to `pipelined_string_equality_3` given the earlier GAPL implementation.
