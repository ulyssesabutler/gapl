`timescale 1ns / 1ps

module uart_receiver
#(
    // Default for UART
    parameter BAUD_RATE = 9600,
    // Assuming 100 MHz clock
    parameter CLOCK_FREQUENCY = 100000000
) (
    input  wire       clock,
    input  wire       reset,

    input  wire       uart,

    output wire [7:0] data,
    output reg        valid,
    input  wire       ready
);

    // Clock Divider
    localparam CLOCK_DIVIDER_SIZE = (CLOCK_FREQUENCY / BAUD_RATE);
    localparam CLOCK_DIVIDER_BITS = $clog2(CLOCK_DIVIDER_SIZE + 1);

    reg [CLOCK_DIVIDER_BITS - 1:0] clock_divider_cycles_remaining;

    // Packet
    /* The current UART packet, 11 bits total since we're not using a parity bit.
     * 1 start bit (with the value 0)
     * 8 data bits (no parity bit)
     * 2 stop bits (with values 1 and 1)
     */
    localparam PACKET_DATA_SIZE        = 8;
    localparam PACKET_DATA_START_INDEX = 1;

    localparam PACKET_SIZE             = 11;
    localparam PACKET_BITS             = $clog2(PACKET_SIZE + 1);

    reg  [PACKET_SIZE - 1:0] packet;
    reg  [PACKET_BITS - 1:0] packet_transmission_index;

    wire [PACKET_DATA_SIZE - 1:0] packet_data = packet[PACKET_DATA_START_INDEX + (PACKET_DATA_SIZE - 1):PACKET_DATA_START_INDEX];

    assign data = packet_data;

    // Stablize the incoming UART connection
    wire stablized_uart;

    debouncer #( .COUNT_MAX(CLOCK_DIVIDER_SIZE / 4), .DEFAULT(1) ) uart_stablizer
    (
        .clock(clock),
        .in(uart),
        .out(stablized_uart)
    );

    // Transmission, set the packet body and transmit
    reg  transmission_running = 0;

    // Start when the UART signal is no longer idle and the current value has been read.
    wire start = stablized_uart == 0 && !valid;

    reg transmission_running_next;
    reg valid_next;
    reg [CLOCK_DIVIDER_BITS - 1:0] clock_divider_cycles_remaining_next;
    reg [PACKET_SIZE - 1:0] packet_next;
    reg [PACKET_BITS - 1:0] packet_transmission_index_next;

    // Debugging
    reg sampling;

    always @(*) begin
        transmission_running_next = transmission_running;
        clock_divider_cycles_remaining_next = clock_divider_cycles_remaining;
        packet_next = packet;
        packet_transmission_index_next = packet_transmission_index;
        valid_next = valid;

        sampling = 0;

        if (!transmission_running && start) begin
            transmission_running_next = 1;

            // We want to take all samples in the middle of the UART clock cycle.
            // Remember, the debounder essentially delays the time it takes a signal change to reach us by a fourth of a UART clock cycle
            clock_divider_cycles_remaining_next = CLOCK_DIVIDER_SIZE / 4;

            packet_next = 0;
            packet_transmission_index_next = 0;
        end

        if (transmission_running) begin
            if (clock_divider_cycles_remaining == 0) begin
                // We are now in the middle of clock cycle. Take a sample
                sampling = 1;
                packet_next[packet_transmission_index] = stablized_uart;

                // And now, reset
                if (packet_transmission_index < (PACKET_SIZE - 1)) begin
                    // Move to the next UART clock cycle / packet index
                    packet_transmission_index_next = packet_transmission_index + 1;
                    // Wait until the beginning of the next clock cycle
                    clock_divider_cycles_remaining_next = CLOCK_DIVIDER_SIZE - 1;
                end else begin
                    transmission_running_next = 0;
                    valid_next = 1;
                end
            end else begin
                // We aren't yet to the middle of the clock cycle, keep waiting
                clock_divider_cycles_remaining_next = clock_divider_cycles_remaining - 1;
            end
        end

        if (!transmission_running && (valid && ready)) begin
            valid_next = 0;
        end
    end

    // Registers
    always @(posedge clock) begin
        if (reset) begin
            transmission_running           <= 0;
            valid                          <= 0;
            clock_divider_cycles_remaining <= 0;
            packet                         <= 0;
            packet_transmission_index      <= 0;
        end else begin
            transmission_running           <= transmission_running_next;
            valid                          <= valid_next;
            clock_divider_cycles_remaining <= clock_divider_cycles_remaining_next;
            packet                         <= packet_next;
            packet_transmission_index      <= packet_transmission_index_next;
        end
    end

endmodule