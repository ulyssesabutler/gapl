module high_throughput_regex_processor
#(
    parameter REPLICATION_FACTOR = 3
) (
    input  wire clock,
    input  wire reset,
    input  wire enable,

    input  wire [8 * REPLICATION_FACTOR - 1:0] data_in,
    output wire                                data_out
);

    regex_main regex
    (
        .clock(clock),
        .reset(reset),
        .enable(enable),

        .i(data_in),
        .o(data_out)
    );

endmodule