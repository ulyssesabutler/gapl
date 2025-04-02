# Gate Array Programming Language [WIP]

This project is seperated into multiple different subproject, each of which depend on the last.
These subproject are, in order
- Lexer / Parser
  - Directory: `antlr`
  - Source: .g4 files
  - Generates: ANTLR visitors, listeners, and parsers in Kotlin
- Compiler
  - Directory: `compiler`
  - Source: Kotlin
  - Generates: GAPL → Verilog compiler
- GAPL Example Source Code
  - Directory: `gapl-src`
  - Source: GAPL code
  - Generates: Verilog code to process data
- Basys 3 Test Harness
  - Directory: `basys`
  - Source: Verilog code to send data to the module produced from the last step
  - Generates: A bitstream to flash to the Basys 3

The compiler is the primary artifact of this project.
The lexer/parser just builds dependencies for the compiler, and the gapl example and Basys 3 test harness are just used to test that compiler.

This project is managed through gradle.
Specifically, all the build steps can be run through the gradle wrapper script, `./gradlew`

Gradle allows you to run tasks from each subproject individually.
For example, if you just want to run the source generation script of the lexer/parser subproject, you can run
```bash
./gradlew :antlr:generateKotlinGrammarSource
```

If you want to run the full pipeline, this can be accomplished using
```bash
./gradlew build
```

And, as usual, you can remove the build artifacts using
```bash
./gradlew clean
```

Below are a few specific, helpful commands.

## Build Compiler

If you just want to build the executable gapl compiler (which, is the main output of this project)

```text
./gradlew :compiler:install
```

This task will create an installable distribution of this application in the `build/install` directory.
Since this is a JVM application, that installation will consist of a set of JAR files, as well as an execution script.

## Running

Once you've built the application, you can invoke the execution script.

```text
 ./compiler/build/install/gapl/bin/gapl [ARGUMENTS]
```

There are currently two modes.
A "test" mode, and a "compile" mode.
To compile a list of files (into a single verilog file), use

```text
 ./compiler/build/install/gapl/bin/gapl -i INPUT_FILE [...] -o OUTPUT_FILE
```

In test mode, you provide a directory that contains many gapl files.
The test script will treat each file in the directory as a separate test case.
It will compile the gapl code in the file, then print that code and the compiled verilog onto the screen.

For instance, you can use the provided `examples` directory.
```text
 ./compiler/build/install/gapl/bin/gapl --test examples
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
