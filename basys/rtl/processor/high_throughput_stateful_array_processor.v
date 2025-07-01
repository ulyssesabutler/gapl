module high_throughput_stateful_array_processor
#(
    parameter REPLICATION_FACTOR = 3,
    parameter ARRAY_HEIGHT,
    parameter ARRAY_WIDTH,
    parameter CELL_WIDTH
) (
    input  wire clock,
    input  wire reset,
    input  wire enable,

    input  wire [8 * REPLICATION_FACTOR - 1:0]                  in_data,
    input  wire                                                 in_valid,
    output wire                                                 in_ready,
    input  wire                                                 in_last,

    output wire [ARRAY_WIDTH * ARRAY_HEIGHT * CELL_WIDTH - 1:0] out_data,
    output wire                                                 out_valid,
    input  wire                                                 out_ready,
    output wire                                                 out_last
);

    count_min_main main
    (
        .clock(clock),
        .reset(reset),
        .enable(enable),

        .i_value(in_data),
        .i_last(in_last),

        .o_value(out_data),
        .o_valid(out_valid)
    );

    assign in_ready = 1;
    assign out_last = 1;

endmodule