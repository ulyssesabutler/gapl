`default_nettype none
`timescale 100fs/100fs

module test_harness
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

    // Packet Parser
    wire [7:0] packet_data;
    wire       packet_valid;
    wire       packet_ready;
    wire       packet_last;

    wire [7:0] packet_fifo_data;
    wire       packet_fifo_valid;
    wire       packet_fifo_ready;
    wire       packet_fifo_last;

    packet_parser parser
    (
        .clock(clock),
        .reset(reset),

        .uart_data(received_data),
        .uart_valid(received_valid),
        .uart_ready(received_ready),

        .packet_data(packet_fifo_data),
        .packet_valid(packet_fifo_valid),
        .packet_ready(packet_fifo_ready),
        .packet_last(packet_fifo_last)
    );

    fifo #( .DATA_WIDTH(8 + 1) ) parsed_packet_fifo
    (
        .clock(clock),
        .reset(reset),

        .in_data({packet_fifo_data, packet_fifo_last}),
        .in_valid(packet_fifo_valid),
        .in_ready(packet_fifo_ready),

        .out_data({packet_data, packet_last}),
        .out_valid(packet_valid),
        .out_ready(packet_ready)
    );

    // Packet constructor
    wire [7:0] processed_packet_data;
    wire       processed_packet_valid;
    wire       processed_packet_ready;
    wire       processed_packet_last;

    packet_constructor constructor
    (
        .clock(clock),
        .reset(reset),

        .packet_data(processed_packet_data),
        .packet_valid(processed_packet_valid),
        .packet_ready(processed_packet_ready),
        .packet_last(processed_packet_last),

        .uart_data(transmitting_data),
        .uart_valid(transmitting_valid),
        .uart_ready(transmitting_ready)
    );

    // Processor Controller
    wire [7:0]  processor_in_data;
    wire        processor_in_valid;
    wire        processor_in_ready;
    wire        processor_in_last;

    wire [7:0]  processor_out_data;
    wire        processor_out_valid;
    wire        processor_out_ready;
    wire        processor_out_last;

    wire        enable;

    wire [31:0] clock_cycles_data;
    wire        clock_cycles_valid;
    wire        clock_cycles_ready;

    processor_controller controller
    (
        .clock(clock),
        .reset(reset),

        .data_ingress_in(packet_data),
        .valid_ingress_in(packet_valid),
        .ready_ingress_in(packet_ready),
        .last_ingress_in(packet_last),

        .data_ingress_out(processor_in_data),
        .valid_ingress_out(processor_in_valid),
        .ready_ingress_out(processor_in_ready),
        .last_ingress_out(processor_in_last),

        .enable(enable),

        .data_egress_in(processor_out_data),
        .valid_egress_in(processor_out_valid),
        .ready_egress_in(processor_out_ready),
        .last_egress_in(processor_out_last),

        .data_egress_out(processed_packet_data),
        .valid_egress_out(processed_packet_valid),
        .ready_egress_out(processed_packet_ready),
        .last_egress_out(processed_packet_last),

        .clock_cycles(clock_cycles_data),
        .clock_cycles_valid(clock_cycles_valid),
        .clock_cycles_ready(clock_cycles_ready)
    );

    // Debugger
    wire debugger_ready;

    led_debugger debugger
    (
        .clock(clock),
        .reset(reset),

        .data_in(processor_in_data),
        .valid_in(processor_in_valid),
        .ready_in(debugger_ready),

        .button_display_next_value(display_next_debug_value_button),
        .leds(debug_leds)
    );

    // Processor
    assign processor_in_ready = debugger_ready;

    string_matching_processor processor
    (
        .clock(clock),
        .reset(reset),
        .enable(enable),

        .in_data(processor_in_data),
        .in_valid(processor_in_valid),
        .in_last(processor_in_last),

        .out_data(processor_out_data),
        .out_valid(processor_out_valid),
        .out_last(processor_out_last)
    );

endmodule
