# FIR Filter

And FIR filter takes a series of inputs and returns a series of outputs.
The $n^{th}$ input is $x[n]$ and the $n^{th}$ output is $y[n]$.
An FIR filter of order $N$ computes each output using the last $N$ inputs.
Each output is equal to the value of sum each previous input, each multiplied by a predefined constant.

$$y[n] = \sum_{i=0}^{N} b_i \cdot x[n - i]$$
where $b_i$ is the value of the impulse response at the $i^{th}$ instant. These values are constant.

```text
function window
(
    parameter size: integer,
    parameter type: interface,
    parameter default: type,
) input: type => output: type[size - 1:0]
{
    // NOTE: This conditionally is statically evaluated. Since size is a constant, this is all done at compile time.
    if (size > 1)
    {
        previous_values register(type, default)[size - 2:0];
        
        input => previous_values[0];
        if (size > 2)
            previous_values[size - 2:1] => previous_values[size - 3:0];
        
        previous_values[size - 2:0] => output[size - 1:1];
    }
    
    input => output[0]
}
```

```text
function zip_with
(
    parameter size: integer,
    parameter type: interface,
    parameter f: function type, type => type,
)  input1: type[size - 1:0], input2: type[size - 1:0] => output: type[size - 1:0]
{
    // Higher-order functions!
    input1[0], input2[0] => f => output[0];
    
    // Recursion!
    if (size > 1)
        input1[size - 1:1], input2[size - 1:1] => zip_with(size - 1, type, f) => output[size - 1:1];
}
```

```text
function fold
(
    parameter size: integer,
    parameter type: interface,
    parameter f: function type, type => type,
) input: type[size - 1:0] => output: type
{
    if (size == 1)
        input => output;
    
    if (size == 2)
        input[0], input[1] => f => output;
        
    if (size > 2)
    {
        input[size - 1:1] => fold(size - 1, type, f) => folded_tail;
        input[0], folded_tail => f => output;
    }
}
```

```text
function dot_product(parameter size: integer) input1: u32int[size - 1:0], input2: u32int[size - 1:0] => output: u32int
{
    input1, input2 => zip_with(size, u32int, multiply) => fold(size, u32int, add) => output;
}
```

```text
function fir_filter(parameter n: integer) input: u32int, coefficients: u32int[n - 1:0], => output: u32int
{
    input => window(size, u32int, 0) => variables;
    coefficients, variables => dot_product => output;
}
```