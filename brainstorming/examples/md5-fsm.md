# MD5

## Interfaces

### `axi_stream`

The actual "data" of the AXI stream should just be
1. The `data` array
2. The `keep` array


## Functions

```
function md5() i: axi_stream => o: axi_stream {
    i => md5_pad() => md5_core() => o;
}

function md5_pad() i: axi_stream => o: axi_stream

function md5_core() i: axi_stream => o: axi_stream {
    // Similar to existing implementation
}
```