`default_nettype none
`timescale 1ns / 1ps

module led_debugger
#(
    parameter MAX_QUEUE_DEPTH_BITS = 8
) (
    input  wire       clock,
    input  wire       reset,

    input  wire [7:0] data_in,
    input  wire       valid_in,
    output wire       ready_in,

    input  wire       button_display_next_value,
    output reg  [7:0] leds
);

    // FIFO Queue to hold data
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

    // Button Handling
    wire debounced_button_display_next_value;

    debouncer debound_button_display_next_value
    (
        .clock(clock),
        .in(button_display_next_value),
        .out(debounced_button_display_next_value)
    );

    reg debounced_button_display_next_value_previous;

    always @(posedge clock) begin
        if (reset) begin
            debounced_button_display_next_value_previous <= 0;
        end else begin
            debounced_button_display_next_value_previous <= debounced_button_display_next_value;
        end
    end

    // Should we display a new value this clock cycle?
    wire display_next_value = !debounced_button_display_next_value_previous && debounced_button_display_next_value;

    reg  [7:0] leds_next;
    reg        ready_queue_next;

    always @(*) begin
        ready_queue_next = 0;
        leds_next         = leds;

        if (display_next_value && valid_queue) begin
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