# Gate Array Programming Language [WIP]

## Testing

Tests for this project can be found in the `src/test` directory.
To run them, using gradle

```text
./gradlew test
```

## Building

This project is managed using the Gradle build system.
To build this project so you can use the command line application, you can use the included gradle wrapper script.

```text
./gradlew install
```

This task will create an installable distribution of this application in the `build/install` directory.
Since this is a JVM application, that installation will consist of a set of JAR files, as well as an execution script.

## Running

Once you've built the application, you can invoke the execution script.

```text
 ./build/install/gapl/bin/gapl [ARGUMENTS]
```

There are currently two modes.
A "test" mode, and a "compile" mode.
To compile a list of files (into a single verilog file), use

```text
 ./build/install/gapl/bin/gapl -i INPUT_FILE [...] -o OUTPUT_FILE
```

In test mode, you provide a directory that contains many gapl files.
The test script will treat each file in the directory as a separate test case.
It will compile the gapl code in the file, then print that code and the compiled verilog onto the screen.

For instance, you can use the provided `examples` directory.
```text
 ./build/install/gapl/bin/gapl --test examples
```

Which will print out
```text
############################
#### TEST: passthrough.g
## GAPL ##

function passthrough() i: wire[32] => o: wire[32]
{
    i => o;
}


## VERILOG ##

module passthrough
(
    input wire [31:0] i_output,
    output wire [31:0] o_input
);
    assign o_input[31:0] = i_output[31:0];
endmodule

############################
#### TEST: passthrough2.g
## GAPL ##

function passthrough() i: wire[32] => o: wire[32]
{
    i => declare t1: wire[32] => declare t2: wire[32] => declare t: wire[32];
    t => o;
}


## VERILOG ##

module passthrough
(
    input wire [31:0] i_output,
    output wire [31:0] o_input
);
    wire [31:0] t1_input;
    wire [31:0] t1_output;
    wire [31:0] t2_input;
    wire [31:0] t2_output;
    wire [31:0] t_input;
    wire [31:0] t_output;
    assign t1_outputs[0:31] = t1_inputs[0:31];
    assign t1_input[31:0] = i_output[31:0];
    assign t2_outputs[0:31] = t2_inputs[0:31];
    assign t2_input[31:0] = t1_output[31:0];
    assign t_outputs[0:31] = t_inputs[0:31];
    assign t_input[31:0] = t2_output[31:0];
    assign o_input[31:0] = t_output[31:0];
endmodule

.
.
.
```
