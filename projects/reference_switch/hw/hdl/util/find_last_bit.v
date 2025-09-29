module find_last_bit
#(
    parameter INPUT_SIZE  = 32,
    localparam INDEX_BITS = $clog2(INPUT_SIZE + 2),

    localparam NEAREST_POW_2 = 2**$clog2(INPUT_SIZE)
)
(
    input  [INPUT_SIZE - 1:0] in,
    output [INDEX_BITS - 1:0] bit_count // Basically, one indexing
);

    wire [INPUT_SIZE - 1:0] last_one_bit_one_hot;

    last_one_detector #(.INPUT_SIZE(INPUT_SIZE)) in_last_one_detector
    (
        .in(in),
        .one_hot(last_one_bit_one_hot)
    );

    one_hot_to_count #(.INPUT_SIZE(INPUT_SIZE)) compute_count
    (
        .one_hot(last_one_bit_one_hot),
        .count(bit_count)
    );

endmodule