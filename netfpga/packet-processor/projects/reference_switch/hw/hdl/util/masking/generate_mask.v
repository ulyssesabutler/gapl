/* GENERATE MASK
 * =============
 */
module generate_mask
#(
    parameter MASK_WIDTH       = 32,

    localparam MASK_WIDTH_BITS = $clog2(MASK_WIDTH)
)
(
    // Module input
    input  [MASK_WIDTH_BITS - 1:0] start_index,
    input  [MASK_WIDTH_BITS - 1:0] end_index,

    output [MASK_WIDTH - 1:0] mask
);

    wire [MASK_WIDTH - 1:0] beginning = (1 << (end_index + 1)) - 1;
    wire [MASK_WIDTH - 1:0] ending    = ~((1 << start_index) - 1);

    assign mask = beginning & ending;

endmodule