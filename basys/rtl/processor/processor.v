`default_nettype none
`timescale 1ns / 1ps

module processor
#(
    parameter TIMER_SIZE    = 32,
    parameter DATA_IN_SIZE  = 8,
    parameter DATA_OUT_SIZE = 8
) (
    input  wire                       clock,
    input  wire                       reset,

    input  wire [DATA_IN_SIZE - 1:0]  in_data,
    input  wire                       in_valid,
    output wire                       in_ready,
    input  wire                       in_last,

    output wire [DATA_OUT_SIZE - 1:0] out_data,
    output wire                       out_valid,
    input  wire                       out_ready,
    output wire                       out_last,

    output wire [TIMER_SIZE - 1:0]    clock_cycles,
    output reg                        clock_cycles_valid,
    input  wire                       clock_cycles_ready
);

    // FIFO Input Queue
    wire [DATA_IN_SIZE - 1:0]  input_queue_data;
    wire                       input_queue_valid;
    wire                       input_queue_ready;
    wire                       input_queue_last;

    fifo #( .DATA_WIDTH(DATA_IN_SIZE + 1) ) input_queue 
    (
        .clock(clock),
        .reset(reset),

        .in_data({in_data, in_last}),
        .in_valid(in_valid),
        .in_ready(in_ready),

        .out_data({input_queue_data, input_queue_last}),
        .out_valid(input_queue_valid),
        .out_ready(input_queue_ready)
    );

    // FIFO Output Queue
    wire [DATA_IN_SIZE - 1:0]  output_queue_data;
    wire                       output_queue_valid;
    wire                       output_queue_ready;
    wire                       output_queue_last;

    fifo #( .DATA_WIDTH(DATA_IN_SIZE + 1) ) output_queue 
    (
        .clock(clock),
        .reset(reset),

        .in_data({output_queue_data, output_queue_last}),
        .in_valid(output_queue_valid),
        .in_ready(output_queue_ready),

        .out_data({out_data, out_last}),
        .out_valid(out_valid),
        .out_ready(out_ready)
    );

    // Processor Controller
    wire [DATA_IN_SIZE - 1:0]  data_ingress_out;
    wire                       valid_ingress_out;
    wire                       ready_ingress_out;
    wire                       last_ingress_out;

    wire                       enable;

    wire [DATA_OUT_SIZE - 1:0] data_egress_in;
    wire                       valid_egress_in;
    wire                       ready_egress_in;
    wire                       last_egress_in;

    wire [TIMER_SIZE - 1:0]    clock_cycles;
    wire                       clock_cycles_valid;
    wire                       clock_cycles_read;

    processor_controller
    #(
        .TIMER_SIZE(TIMER_SIZE),
        .DATA_IN_SIZE(DATA_IN_SIZE),
        .DATA_OUT_SIZE(DATA_OUT_SIZE)
    ) controller (
        .clock(clock),
        .reset(reset),

        .data_ingress_in(input_queue_data),
        .valid_ingress_in(input_queue_valid),
        .ready_ingress_in(input_queue_ready),
        .last_ingress_in(input_queue_last),

        .data_ingress_out(data_ingress_out),
        .valid_ingress_out(valid_ingress_out),
        .ready_ingress_out(ready_ingress_out),
        .last_ingress_out(last_ingress_out),

        .enable(enable),

        .data_egress_in(data_egress_in),
        .valid_egress_in(data_egress_in),
        .ready_egress_in(data_egress_in),
        .last_egress_in(data_egress_in),

        .data_egress_out(output_queue_data),
        .valid_egress_out(output_queue_valid),
        .ready_egress_out(output_queue_ready),
        .last_egress_out(output_queue_last),

        .clock_cycles(clock_cycles),
        .clock_cycles_valid(clock_cycles_valid),
        .clock_cycles_ready(clock_cycles_ready)
    );

    // Processor
    add_3 example_processor
    (
        .clock(clock),
        .reset(reset),
        .enable(enable),

        .in_data(data_ingress_out),
        .in_valid(valid_ingress_out),

        .out_data(data_egress_in),
        .out_valid(valid_egress_in),
        .out_ready(ready_egress_in)
    );

    assign last_egress_in = last_ingress_out;
    assign ready_ingress_out = 1;

endmodule