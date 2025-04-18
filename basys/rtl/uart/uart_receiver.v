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
    localparam CLOCK_DIVIDER_SIZE = ((CLOCK_FREQUENCY + (BAUD_RATE / 2)) / BAUD_RATE);
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

    localparam PACKET_SIZE             = 9;
    localparam PACKET_BITS             = $clog2(PACKET_SIZE + 1);

    reg  [PACKET_SIZE - 1:0] packet;
    reg  [PACKET_BITS - 1:0] packet_transmission_index;

    wire [PACKET_DATA_SIZE - 1:0] packet_data = packet[PACKET_DATA_START_INDEX + (PACKET_DATA_SIZE - 1):PACKET_DATA_START_INDEX];

    assign data = packet_data;

    // Stablize the incoming UART connection
    wire stablized_uart;

    stabilize_async #( .DEFAULT(1) ) uart_stablizer
    (
        .clock(clock),
        .reset(reset),
        .async_input(uart),
        .sync_output(stablized_uart)
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

    reg [CLOCK_DIVIDER_BITS:0] current_sample_1s;
    reg [CLOCK_DIVIDER_BITS:0] current_sample_0s;

    reg [CLOCK_DIVIDER_BITS:0] current_sample_1s_next;
    reg [CLOCK_DIVIDER_BITS:0] current_sample_0s_next;

    always @(*) begin
        transmission_running_next = transmission_running;
        clock_divider_cycles_remaining_next = clock_divider_cycles_remaining;
        packet_next = packet;
        packet_transmission_index_next = packet_transmission_index;
        valid_next = valid;
        current_sample_0s_next = current_sample_0s;
        current_sample_1s_next = current_sample_1s;

        if (!transmission_running && start) begin
            transmission_running_next = 1;

            clock_divider_cycles_remaining_next = CLOCK_DIVIDER_SIZE - 1;

            current_sample_0s_next = 0;
            current_sample_1s_next = 1;

            packet_next = 0;
            packet_transmission_index_next = 0;
        end

        if (transmission_running) begin
            if (clock_divider_cycles_remaining == 0) begin
                // We are now in the middle of clock cycle. Take a sample
                packet_next[packet_transmission_index] = stablized_uart;

                if (current_sample_1s > current_sample_0s) begin
                    packet_next[packet_transmission_index] = 1;
                end else begin
                    packet_next[packet_transmission_index] = 0;
                end

                // And now, reset
                if (packet_transmission_index < (PACKET_SIZE - 1)) begin
                    // Move to the next UART clock cycle / packet index
                    packet_transmission_index_next = packet_transmission_index + 1;
                    // Wait until the beginning of the next clock cycle
                    clock_divider_cycles_remaining_next = CLOCK_DIVIDER_SIZE - 1;

                    // Reset the counters
                    current_sample_0s_next = stablized_uart == 0;
                    current_sample_1s_next = stablized_uart == 1;
                end else begin
                    transmission_running_next = 0;
                    valid_next = 1;
                end
            end else begin
                current_sample_0s_next = current_sample_0s + 1;
                current_sample_1s_next = current_sample_1s + 1;
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
            current_sample_0s              <= 0;
            current_sample_1s              <= 0;
        end else begin
            transmission_running           <= transmission_running_next;
            valid                          <= valid_next;
            clock_divider_cycles_remaining <= clock_divider_cycles_remaining_next;
            packet                         <= packet_next;
            packet_transmission_index      <= packet_transmission_index_next;
            current_sample_0s              <= current_sample_0s_next;
            current_sample_1s              <= current_sample_1s_next;
        end
    end

endmodule