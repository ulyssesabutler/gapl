# Functions

## `map`: `stream` => `stream`

This function takes a single parameter, an operation.

It consumes one input stream, and produces one output stream.
Each element is transformed using the

## `map_each`: `buffer` => `buffer`

Whereas the previous function transform each item individually before transforming the next, this transforms all elements in parallel.