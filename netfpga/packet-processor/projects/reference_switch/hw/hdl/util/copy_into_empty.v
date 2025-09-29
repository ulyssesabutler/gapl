module copy_into_empty
#(
    parameter SRC_DATA_WIDTH   = 256,
    parameter DEST_DATA_WIDTH  = 256,

    localparam SRC_KEEP_WIDTH  = SRC_DATA_WIDTH / 8,
    localparam DEST_KEEP_WIDTH = DEST_DATA_WIDTH / 8
)
(
    input  [SRC_DATA_WIDTH - 1:0]  src_data_in,
    input  [SRC_KEEP_WIDTH - 1:0]  src_keep_in,

    input  [DEST_DATA_WIDTH - 1:0]  dest_data_in,
    input  [DEST_KEEP_WIDTH - 1:0]  dest_keep_in,

    output [SRC_DATA_WIDTH - 1:0] src_data_out,
    output [SRC_KEEP_WIDTH - 1:0] src_keep_out,

    output [DEST_DATA_WIDTH - 1:0] dest_data_out,
    output [DEST_KEEP_WIDTH - 1:0] dest_keep_out
);

    wire    [31:0] first_non_empty_in_dest_data_out;
    wire    [31:0] first_non_empty_in_dest_keep_out;

    first_null_index
    #(
        .DATA_WIDTH(DEST_KEEP_WIDTH)
    )
    first_non_empty_in_dest_keep_out_calc
    (
        .data(dest_keep_in),
        .index(first_non_empty_in_dest_keep_out)
    );
    assign first_non_empty_in_dest_data_out = first_non_empty_in_dest_keep_out * 8;

    assign dest_data_out = (src_data_in << first_non_empty_in_dest_data_out) | dest_data_in;
    assign dest_keep_out = (src_keep_in << first_non_empty_in_dest_keep_out) | dest_keep_in;

    assign src_data_out  = src_data_in >> (DEST_DATA_WIDTH - first_non_empty_in_dest_data_out);
    assign src_keep_out  = src_keep_in >> (DEST_KEEP_WIDTH - first_non_empty_in_dest_keep_out);

endmodule
