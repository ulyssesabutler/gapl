`default_nettype none
`timescale 1ns / 1ps

module led_debugger
#(
    parameter MAX_QUEUE_DEPTH_BITS = 4
) (
    input  wire       clock,
    input  wire       reset,

    input  wire [7:0] data_in,
    input  wire       valid_in,
    output wire       ready_in,

    input  wire       display_next,
    output reg  [7:0] leds
);

    wire [7:0] data_queue;
    wire       valid_queue;
    reg        ready_queue;

    fifo #( .DATA_WIDTH(8), .MAX_DEPTH_BITS(MAX_QUEUE_DEPTH_BITS) ) queue
    (
        .clock(clock),
        .reset(reset),
        
        .in_data(data_in),
        .in_valid(valid_in),
        .in_ready(ready_in),
        
        .out_data(data_queue),
        .out_valid(valid_queue),
        .out_ready(ready_queue)
    );

    reg  [7:0] leds_next;
    reg        ready_queue_next;

    always @(*) begin
        ready_queue_next = 0;
        leds_next         = leds;

        if (display_next && valid_queue) begin
            ready_queue_next = 1;
            leds_next         = data_queue;
        end
    end

    always @(posedge clock) begin
        if (reset) begin
            leds        <= 0;
            ready_queue <= 0;
        end else begin
            leds        <= leds_next;
            ready_queue <= ready_queue_next;
        end
    end

endmodule