# MD5

## Interfaces

```
// Layer 1
interface partial_byte_array(size: integer) {
    data: byte[size];
    valid: boolean[size];
}
interface md5_digest_hardware wire[128];

// Layer 2
datatype byte_array_stream(size: integer) stream(partial_byte_array(size));
datatype md5_digest_value                 single(md5_digest_hardware);

// Layer 3
protocol axi_stream byte_array_stream(32)@ready_valid;
protocol md5_stream byte_array_stream(64)@ready_valid;
protocol md5_digest md5_digest_value@ready_valid;
```

## Functions

```
// This function will be plugged directly into the NetFPGA pipeline
function md5_netfpga() i: axi_stream => o: axi_stream {
    i => axis_to_md5_stream() => md5() => md5_digest_to_axis() => o;
}

// This is the pure MD5 function. It takes a stream as an input, and produces a single message digest as an output.
function md5() i: md5_stream => o: md5_digest {
    i => md5_pad() => md5_core() => o;
}

// This function essentially handles the logic of Layer 2
// For input streams of size m, the output stream size will be m or m+1 
function md5_pad() i: md5_stream => o: md5_stream { ... }

// The fold here is similar to an aetherling reduce_t.
function md5_core() i: md5_stream => o: md5_digest {
    ... => fold(md5_hash_block) => o;
}

// This function operates purely on Level 1
function md5_hash_block() i: partial_byte_array(64), input_state: md5_digest_hardware => output_state: md5_digest_hardware { ... }
```