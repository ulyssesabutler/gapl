`default_nettype none
`timescale 100fs/100fs

module high_throughput_test_harness
#(
    parameter BAUD_RATE = 9600, // Default for UART
    parameter CLOCK_FREQUENCY = 100000000, // 100 MHz
    parameter REPLICATION_FACTOR = 3
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

    // Collector
    wire [8 * REPLICATION_FACTOR - 1:0] collected_data;
    wire                                collected_valid;
    wire                                collected_ready;
    wire                                collected_last;

    collector #( .DATA_WIDTH(8), .ITEM_COUNT(REPLICATION_FACTOR) ) uart_item_collector
    (
        .clock(clock),
        .reset(reset),

        .in_data(packet_data),
        .in_valid(packet_valid),
        .in_ready(packet_ready),
        .in_last(packet_last),

        .out_data(collected_data),
        .out_valid(collected_valid),
        .out_ready(collected_ready),
        .out_last(collected_last)
    );

    // Packet Constructor
    wire       processed_packet_data;
    wire       processed_packet_valid;
    wire       processed_packet_ready;
    wire       processed_packet_last;

    wire [31:0] clock_cycles_data;
    wire        clock_cycles_valid;
    wire        clock_cycles_ready;

    packet_constructor constructor
    (
        .clock(clock),
        .reset(reset),

        .packet_data(processed_packet_data),
        .packet_valid(processed_packet_valid),
        .packet_ready(processed_packet_ready),
        .packet_last(processed_packet_last),

        .clock_data(clock_cycles_data),
        .clock_valid(clock_cycles_valid),
        .clock_ready(clock_cycles_ready),

        .uart_data(transmitting_data),
        .uart_valid(transmitting_valid),
        .uart_ready(transmitting_ready)
    );

    // Processor Controller
    wire [8 * REPLICATION_FACTOR - 1:0]  processor_in_data;
    wire                                 processor_in_valid;
    wire                                 processor_in_ready;
    wire                                 processor_in_last;

    wire [7:0]                           processor_out_data;
    wire                                 processor_out_valid;
    wire                                 processor_out_ready;
    wire                                 processor_out_last;

    wire                                 enable;

    processor_controller #( .DATA_IN_SIZE(8 * REPLICATION_FACTOR), .DATA_OUT_SIZE(8) ) controller
    (
        .clock(clock),
        .reset(reset),

        .data_ingress_in(collected_data),
        .valid_ingress_in(collected_valid),
        .ready_ingress_in(collected_ready),
        .last_ingress_in(collected_last),

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

    // Processor
    high_throughput_stateful_processor #( .REPLICATION_FACTOR(REPLICATION_FACTOR) ) processor
    (
        .clock(clock),
        .reset(reset),
        .enable(enable),

        .in_data(processor_in_data),
        .in_valid(processor_in_valid),
        .in_ready(processor_in_ready),
        .in_last(processor_in_last),

        .out_data(processor_out_data),
        .out_valid(processor_out_valid),
        .out_ready(processor_out_ready),
        .out_last(processor_out_last)
    );

endmodule