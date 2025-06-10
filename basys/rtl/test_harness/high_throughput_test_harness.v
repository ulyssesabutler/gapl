`default_nettype none
`timescale 100fs/100fs

module high_throughput_test_harness
#(
    parameter BAUD_RATE = 9600, // Default for UART
    parameter CLOCK_FREQUENCY = 100000000 // 100 MHz
) (
    input  wire       clock,

    input  wire       reset,

    input  wire       uart_receive,
    output wire       uart_transmit,

    input  wire       display_next_debug_value_button,
    output wire [7:0] debug_leds
);

    // UART
    wire [7:0] transmitting_data;
    wire       transmitting_valid;
    wire       transmitting_ready;

    wire [7:0] received_data;
    wire       received_valid;
    wire       received_ready;

    uart_controller #( .BAUD_RATE(BAUD_RATE), .CLOCK_FREQUENCY(CLOCK_FREQUENCY) ) uart
    (
        .clock(clock),
        .reset(reset),

        .receive_uart(uart_receive),
        .transmit_uart(uart_transmit),

        .transmit_data(transmitting_data),
        .transmit_valid(transmitting_valid),
        .transmit_ready(transmitting_ready),

        .receive_data(received_data),
        .receive_valid(received_valid),
        .receive_ready(received_ready)
    );

endmodule