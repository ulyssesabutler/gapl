`default_nettype none
`timescale 1ns / 1ps

module switch_debugger
#(
    parameter MAX_QUEUE_DEPTH_BITS = 4
) (
    input  wire       clock,
    input  wire       reset,

    input  wire       send_next,
    input  wire [7:0] switches,

    output wire [7:0] data_out,
    output wire       valid_out,
    input  wire       ready_out
);

    reg  [7:0] data_queue;
    reg        valid_queue;
    wire       ready_queue;

    fifo #( .DATA_WIDTH(8), .MAX_DEPTH_BITS(MAX_QUEUE_DEPTH_BITS) ) queue
    (
        .clock(clock),
        .reset(reset),
        
        .in_data(data_queue),
        .in_valid(valid_queue),
        .in_ready(ready_queue),
        
        .out_data(data_out),
        .out_valid(valid_out),
        .out_ready(ready_out)
    );

    reg  [7:0] data_queue_next;
    reg        valid_queue_next;

    always @(*) begin
        data_queue_next  = 0;
        valid_queue_next = 0;

        if (send_next && ready_queue) begin
            data_queue_next  = switches;
            valid_queue_next = 1;
        end
    end

    always @(posedge clock) begin
        if (reset) begin
            data_queue  <= 0;
            valid_queue <= 0;
        end else begin
            data_queue  <= data_queue_next;
            valid_queue <= valid_queue_next;
        end
    end

endmodule