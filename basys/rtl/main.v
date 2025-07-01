`default_nettype none
`timescale 100fs/100fs

module main
(
    input  wire       clock,

    input  wire       button_center,

    input  wire       uart_receive,
    output wire       uart_transmit,

    input  wire       button_right,
    output wire [7:0] leds
);

    wire reset;

    debouncer debounce_reset
    (
        .clock(clock),
        .in(button_center),
        .out(reset)
    );

    /*
    test_harness harness
    (
        .clock(clock),

        .reset(reset),

        .uart_receive(uart_receive),
        .uart_transmit(uart_transmit),

        .display_next_debug_value_button(button_right),
        .debug_leds(leds)
    );
    */

    high_throughput_array_output_test_harness harness
    (
        .clock(clock),

        .reset(reset),

        .uart_receive(uart_receive),
        .uart_transmit(uart_transmit)
    );

endmodule