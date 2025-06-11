`default_nettype none
`timescale 100fs/100fs

module replicate
#(
    parameter ITEM_SIZE = 8,
    parameter REPLICATION_FACTOR = 3
) (
    input  wire [ITEM_SIZE - 1:0]                      in_data,
    input  wire                                        in_valid,
    output wire                                        in_ready,

    output wire [ITEM_SIZE * REPLICATION_FACTOR - 1:0] out_data,
    output wire                                        out_valid,
    input  wire                                        out_ready
);

    genvar i;
    generate
        for (i = 0; i < REPLICATION_FACTOR; i = i + 1) begin
            assign out_data[ITEM_SIZE * (i + 1) - 1:ITEM_SIZE * i] = in_data;
        end
    endgenerate

    assign out_valid = in_valid;
    assign in_ready  = out_ready;

endmodule