# Basic Structure

:warning: Note that `src` is symlinked to `../gapl-example/src` for convenience. :warning:

Every test is defined by two files:
```
src/NAME.gapl
test-drivers/test-NAME.v
```

The former is the GAPL source, the latter is a bit of hand-written Verilog
to instantiate one of the GAPL-generated modules, pass some inputs, monitor
the output, etc. (Recommended to use `$monitor(...)` feature of Verilog, which
is supported by the `iverilog` simulator.)

The two files above will be used within `runtest`. See file `runtest` for
configuration options, e.g., path for `iverilog`.

# Example Usage

Example usage of script `runtest`:
```
$ ./runtest vector-map
```

This does the following:
  1. run GAPL on `src/vector-map.gapl`
  2. output written to `compiled-verilog/vector-map.v`
  3. run iverilog simulation, using
     `compiled-verilog/vector-map.v` and `test-drivers/test-vector-map.v`

# Creating Your Own Test

To define and run a new test called FOO:
  1. create `src/FOO.gapl`
  2. create `test-drivers/test-FOO.v`
  3. do `./runtest FOO`

# Installing iverilog

Source: https://github.com/steveicarus/iverilog
Useful guide: https://steveicarus.github.io/iverilog/usage/index.html

The following worked for me (Sam) with no major issues:
```bash
$ git clone https://github.com/steveicarus/iverilog
$ cd iverilog
$ sh autoconf.sh
$ ./configure --prefix="$HOME/.local"
$ make
$ make install  # installs into "$HOME/.local/..." as specified above
```