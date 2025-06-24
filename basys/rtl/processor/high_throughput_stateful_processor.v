module high_throughput_stateful_processor
#(
    parameter REPLICATION_FACTOR = 3
) (
    input  wire clock,
    input  wire reset,
    input  wire enable,

    input  wire [8 * REPLICATION_FACTOR - 1:0] in_data,
    input  wire                                in_valid,
    output wire                                in_ready,
    input  wire                                in_last,

    output wire [7:0]                          out_data,
    output wire                                out_valid,
    input  wire                                out_ready,
    output wire                                out_last
);

    wire out_data_boolean;

    //regex_main main
    state_transition_main main
    (
        .clock(clock),
        .reset(reset),
        .enable(enable),

        .i_value(in_data),
        .i_last(in_last),

        .o_value(out_data_boolean),
        .o_valid(out_valid)
    );

    assign in_ready = 1;
    assign out_last = 1;
    assign out_data = {{7{0}}, out_data_boolean};

endmodule