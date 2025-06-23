`default_nettype none
`timescale 100fs/100fs

module high_throughput_test_harness
#(
    parameter BAUD_RATE = 9600, // Default for UART
    parameter CLOCK_FREQUENCY = 100000000, // 100 MHz
    parameter REPLICATION_FACTOR = 12
) (
    input  wire       clock,

    input  wire       reset,

    input  wire       uart_receive,
    output wire       uart_transmit
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

    // Replication
    wire [8 * REPLICATION_FACTOR - 1:0] replicated_data;
    wire        replicated_valid;
    wire        replicated_ready;

    replicate #( .REPLICATION_FACTOR(REPLICATION_FACTOR) ) replicate_uart
    (
        .in_data(received_data),
        .in_valid(received_valid),
        .in_ready(received_ready),

        .out_data(replicated_data),
        .out_valid(replicated_valid),
        .out_ready(replicated_ready)
    );

    // TODO: Controller

    // Processor
    wire enable = 1;

    high_throughput_stateful_processor #( .REPLICATION_FACTOR(REPLICATION_FACTOR) ) processor
    (
        .clock(clock),
        .reset(reset),
        .enable(enable),

        .data_in(replicated_data),
        .data_out(transmitting_data)
    );

    assign transmitting_valid = 1;

endmodule