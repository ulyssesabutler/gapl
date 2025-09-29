module apply_byte_mask
#(
    parameter TDATA_WIDTH  = 256,

    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
)
(
    input  [TDATA_WIDTH - 1:0] processed_data_in,
    input  [TDATA_WIDTH - 1:0] unprocessed_data_in,
    input  [TKEEP_WIDTH - 1:0] byte_mask,

    output [TDATA_WIDTH - 1:0] data_out
);

    wire [TDATA_WIDTH - 1:0] bit_mask;

    wire [TDATA_WIDTH - 1:0] processed_data_masked;
    wire [TDATA_WIDTH - 1:0] unprocessed_data_masked;

    byte_to_bit_mask bit_mask_generator
    (
        .byte_mask(byte_mask),
        .bit_mask(bit_mask)
    );

    assign processed_data_masked   = processed_data_in & bit_mask;
    assign unprocessed_data_masked = unprocessed_data_in & ~bit_mask;

    assign data_out = processed_data_masked | unprocessed_data_masked;

endmodule
