`default_nettype none
`timescale 1ns / 1ps

module collector
#(
    parameter DATA_WIDTH = 8,
    parameter ITEM_COUNT = 3,
    parameter ITEM_COUNT_BITS = $clog2(ITEM_COUNT + 1)
) (
    input  wire                                   clock,
    input  wire                                   reset,

    input  wire [DATA_WIDTH - 1:0]                in_data,
    input  wire                                   in_valid,
    output wire                                   in_ready,
    input  wire                                   in_last,

    output wire [(DATA_WIDTH * ITEM_COUNT) - 1:0] out_data,
    output wire                                   out_valid,
    input  wire                                   out_ready,
    output wire                                   out_last
);

    wire [DATA_WIDTH - 1:0] ingress_data;
    wire                    ingress_valid;
    wire                    ingress_ready;
    wire                    ingress_last;

    fifo #( .DATA_WIDTH(DATA_WIDTH + 1) ) ingress_fifo
    (
        .clock(clock),
        .reset(reset),

        .in_data({in_data, in_last}),
        .in_valid(in_valid),
        .in_ready(in_ready),

        .out_data({ingress_data, ingress_last}),
        .out_valid(ingress_valid),
        .out_ready(ingress_ready)
    );

    wire [(DATA_WIDTH * ITEM_COUNT) - 1:0] egress_data;
    wire                                   egress_valid;
    wire                                   egress_ready;
    wire                                   egress_last;

    fifo #( .DATA_WIDTH(DATA_WIDTH * ITEM_COUNT + 1) ) egress_fifo
    (
        .clock(clock),
        .reset(reset),

        .in_data({egress_data, egress_last}),
        .in_valid(egress_valid),
        .in_ready(egress_ready),

        .out_data({out_data, out_last}),
        .out_valid(out_valid),
        .out_ready(out_ready)
    );

    reg [DATA_WIDTH - 1:0]      buffer           [0:ITEM_COUNT - 1];
    reg                         buffer_last;
    reg [ITEM_COUNT_BITS - 1:0] buffer_size;

    reg [DATA_WIDTH - 1:0]      buffer_next      [0:ITEM_COUNT - 1];
    reg                         buffer_last_next;
    reg [ITEM_COUNT_BITS - 1:0] buffer_size_next;

    assign ingress_ready = (buffer_size != ITEM_COUNT) && !buffer_last;
    assign egress_valid = !ingress_ready;

    integer buffer_next_iterator;
    always @(*) begin
        for (buffer_next_iterator = 0; buffer_next_iterator < ITEM_COUNT; buffer_next_iterator = buffer_next_iterator + 1)
            buffer_next[buffer_next_iterator] = buffer[buffer_next_iterator];

        buffer_last_next = buffer_last;
        buffer_size_next = buffer_size;

        if (ingress_ready && ingress_valid) begin
            buffer_next[buffer_size] = ingress_data;
            buffer_last_next         = buffer_last || ingress_last;
            buffer_size_next         = buffer_size + 1;
        end

        if (egress_ready && egress_valid) begin
            for (buffer_next_iterator = 0; buffer_next_iterator < ITEM_COUNT; buffer_next_iterator = buffer_next_iterator + 1)
                buffer_next[buffer_next_iterator] = 0;

            buffer_size_next = 0;
            buffer_last_next = 0;
        end
    end

    genvar i;
    generate
        for (i = 0; i < ITEM_COUNT; i = i + 1) begin : flatten_buffer
            assign egress_data[(i + 1) * DATA_WIDTH - 1 -: DATA_WIDTH] = buffer[i];
        end
    endgenerate

    assign egress_last = buffer_last;

    integer buffer_iterator;
    always @(posedge clock) begin
        if (reset) begin
            buffer_size <= 0;
            buffer_last <= 0;
            for (buffer_iterator = 0; buffer_iterator < ITEM_COUNT; buffer_iterator = buffer_iterator + 1)
                buffer[buffer_iterator] <= 0;
        end else begin
            buffer_size <= buffer_size_next;
            buffer_last <= buffer_last_next;
            for (buffer_iterator = 0; buffer_iterator < ITEM_COUNT; buffer_iterator = buffer_iterator + 1)
                buffer[buffer_iterator] <= buffer_next[buffer_iterator];
        end
    end

endmodule