`timescale 1ns / 1ps

module uart_controller
#(
    // Default for UART
    parameter BAUD_RATE = 9600,
    // Assuming 100 MHz clock
    parameter CLOCK_FREQUENCY = 100000000,

    parameter MAX_QUEUE_DEPTH_BITS = 4

    // TODO: Add parameters for UART transmission. E.g., bits per packet, parity bit presence, etc.
) (
    input  wire       clock,
    input  wire       reset,

    input  wire       receive_uart,
    output wire       transmit_uart,

    input  wire [7:0] transmit_data,
    input  wire       transmit_valid,
    output wire       transmit_ready,

    output wire [7:0] receive_data,
    output wire       receive_valid,
    input  wire       receive_ready
);

    // RECEIVER

    wire [7:0] uart_receiver_data;
    wire       uart_receiver_ready;
    wire       uart_receiver_valid;

    // Receiver UART
    uart_receiver
    #(
        .BAUD_RATE(BAUD_RATE),
        .CLOCK_FREQUENCY(CLOCK_FREQUENCY)
    )
    receiver
    (
        .clock(clock),
        .reset(reset),

        .uart(receive_uart),

        .data(uart_receiver_data),
        .valid(uart_receiver_valid),
        .ready(uart_receiver_ready)
    );

    // Receiver FIFO
    fifo #( .DATA_WIDTH(8), .MAX_DEPTH_BITS(MAX_QUEUE_DEPTH_BITS) ) receiver_fifo
    (
        .clock(clock),
        .reset(reset),
        
        .in_data(uart_receiver_data),
        .in_valid(uart_receiver_valid),
        .in_ready(uart_receiver_ready),
        
        .out_data(receive_data),
        .out_valid(receive_valid),
        .out_ready(receive_ready)
    );

    // TRANSMITTER

    wire [7:0] uart_transmitter_data;
    wire       uart_transmitter_ready;
    wire       uart_transmitter_valid;

    // Transmitter FIFO
    fifo #( .DATA_WIDTH(8), .MAX_DEPTH_BITS(MAX_QUEUE_DEPTH_BITS) ) transmitter_fifo
    (
        .clock(clock),
        .reset(reset),
        
        .in_data(transmit_data),
        .in_valid(transmit_valid),
        .in_ready(transmit_ready),
        
        .out_data(uart_transmitter_data),
        .out_valid(uart_transmitter_valid),
        .out_ready(uart_transmitter_ready)
    );

    // Transmitter UART
    uart_transmitter
    #(
        .BAUD_RATE(BAUD_RATE),
        .CLOCK_FREQUENCY(CLOCK_FREQUENCY)
    )
    transmitter
    (
        .clock(clock),
        .reset(reset),
        
        .data(uart_transmitter_data),
        .valid(uart_transmitter_valid),
        .ready(uart_transmitter_ready),

        .uart(transmit_uart)
    );

endmodule