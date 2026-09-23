# Adding a new NetFPGA application

A NetFPGA "application" is a GAPL packet-body processor plus the test vectors that pin down its
correctness. This doc covers the steps to add one and, in particular, how to write real
(non-placeholder) test vectors - the part that isn't obvious from just reading an existing app's
`gapl-processor.gapl`.

## Directory layout

```
netfpga/src/<app-name>/
    gapl-processor.gapl   # the application itself - shared by every variation below
    test.properties        # test vectors - also shared by every variation
    <variation-name>/
        compile.properties  # per-variation compiler settings (retime, flatten, clock period, ...)
```

`gapl-processor.gapl` and `test.properties` live one level *above* the variation directories on purpose:
retiming/flattening are meant to be semantics-preserving, so the same GAPL source and the same test
vectors must hold regardless of which variation is selected (see the comment above `testPropsFile`
in `netfpga/build.gradle.kts`).

## The GAPL contract

`gapl-processor.gapl` must define a function named exactly `packet_body_processor`, taking and returning
the standard packet-body interface:

```gapl
interface netfpga_packet_body {
    data: wire[256];
    keep: boolean[32];
    valid: boolean;
    last: boolean;
}

function packet_body_processor() i: netfpga_packet_body => o: netfpga_packet_body {
    ...
}
```

`data` is one 256-bit (32-byte) beat of packet body per cycle. Look at an existing app close to
what you're building for the shape of the rest - `bloom-filter` and `crc32` for a single
combinational pass over one beat with no state across beats, `md5-stream`/`md5-stream-simple` for
an app that needs to accumulate state across a multi-beat packet in a register.

Once `gapl-processor.gapl` exists, `./gradlew :compiler:installDist` followed by
`compiler/build/install/gapl/bin/gapl --flatten all -o /tmp/out.v netfpga/src/<app-name>/gapl-processor.gapl`
is the fastest way to get a real compiler error message while you're still iterating - much faster
than waiting on a full Gradle/Verilator/Vivado round trip.

## Writing `test.properties`

This is consumed by two independent test harnesses - `netfpga/sim-kernel-test` (fast, pure
`simengine`, no Verilog) and `netfpga/kernel-test` (Verilator, compiles and runs the real generated
Verilog) - both reading the exact same file, so one correct `test.properties` exercises both. The
format:

```properties
testInputs=\
  <hex packet 1>,\
  <hex packet 2>,\
  ...
testExpectedOutputs=\
  <hex packet 1 expected output>,\
  <hex packet 2 expected output>,\
  ...
```

`testInputs` and `testExpectedOutputs` must have the same number of comma-separated entries - one
expected-output entry per input entry (i.e. per *packet*, not per beat).

### Bit and byte ordering - the part that isn't obvious

- **Within a `wire[N]`, bit index 0 is the least-significant bit** (`2^0`), confirmed by how
  `literal()` and `add()` are lowered throughout the analyzer/simengine/compiler. Index `N-1` is the
  most significant bit.
- **Each hex string is one packet**, encoded the natural way: read left to right, each pair of hex
  digits is one byte, in the order those bytes are actually transmitted. The leftmost byte is the
  first byte sent.
- **A packet's hex string is split into 256-bit (64-hex-char) beats** by the harness
  (`hexToBeats` in `sim-kernel-test/.../Main.kt`, `string_to_nf_stream`/`make_messages` in
  `kernel-test/test.cpp`) - full 64-char chunks left to right, and if the total length isn't a
  multiple of 64, the short leftover beat is sent **first**, not last. The final beat is always the
  one marked `last`.
- **Every beat of the expected output is checked**, not just the last one - if your application
  echoes/transforms every beat (rather than emitting one result only at the end of a packet, the
  way `md5-stream` does), your expected-output hex string needs one 64-hex-char chunk per input
  beat, concatenated in order. `crc32/test.properties`'s multi-beat vector is an example of this.
- **`o.valid` is implicit, not compared as a field**: a cycle where `valid` is false simply produces
  no output beat at all. `data`, `keep`, and `last` on beats that *do* appear are all checked.

### Getting the expected values right

Don't hand-derive expected output hex by reasoning about the GAPL in your head - compute it with an
independent reference implementation (e.g. a short Python script implementing the actual algorithm),
then place the result according to how your `gapl-processor.gapl` actually packs its output bits into the
256-bit `data` field (which half/end of the beat the result occupies, and whether the rest is
zero-padded) - that packing is a choice your own code makes, so check it against your code, not
against another app's convention. Once you have candidate hex strings, run them through
`sim-kernel-test` (see below) - it's far faster than Verilator for iterating until they pass, and
only then worth also confirming through the Verilator path.

## Running the tests

Fast path, no Verilog/Vivado involved - compiles straight from GAPL source through `simengine`:

```
./gradlew :netfpga:runSimKernelTest -PprogramName=<app-name> -PprogramVariationName=<variation-name>
```

Slower, higher-fidelity path - compiles GAPL to real Verilog and runs it through Verilator:

```
./gradlew :netfpga:runKernelTest -PprogramName=<app-name> -PprogramVariationName=<variation-name>
```

Both read the same `test.properties` and print a pass/fail per packet/beat. Get a new application
passing `runSimKernelTest` first; only reach for `runKernelTest` once the vectors themselves are
already verified correct, since a mismatch there could mean either a real Verilog-generation bug or
just a test vector you haven't yet confirmed independently.

## Program variations (brief)

Each subdirectory under `netfpga/src/<app-name>/` is a "variation" - the same `gapl-processor.gapl`
compiled with different settings (retiming on/off, which solver, flatten mode, clock period, ...),
each with its own `compile.properties`. The exact set of available settings is subject to change -
see `netfpga/build.gradle.kts`'s own property-reading code (`propString`/`propBool` calls) and an
existing variation's `compile.properties` for the current options, rather than trusting this doc to
stay in sync. At minimum, a new application needs at least one variation directory (an empty or
`retime=false` `compile.properties` is enough to get started) before `runKernelTest`, `runSimulation`,
or a hardware build can target it.
