`timescale 1ns / 1ps

module uart_transmitter
#(
    // Default for UART
    parameter BAUD_RATE = 9600,
    // Assuming 100 MHz clock
    parameter CLOCK_FREQUENCY = 100000000

    // TODO: Add parameters for UART transmission. E.g., bits per packet, parity bit presence, etc.
) (
    input  wire       clock,
    input  wire       reset,

    input  wire [7:0] data,
    input  wire       valid,
    output wire       ready,

    output reg        uart
);

    // Clock Divider
    localparam CLOCK_DIVIDER_SIZE = (CLOCK_FREQUENCY / BAUD_RATE);
    localparam CLOCK_DIVIDER_BITS = $clog2(CLOCK_DIVIDER_SIZE + 1);

    reg [CLOCK_DIVIDER_BITS - 1:0] clock_divider_cycle_count = 0;

    // Packet
    /* The current UART packet, 11 bits total since we're not using a parity bit.
     * 1 start bit (with the value 0)
     * 8 data bits (no parity bit)
     * 2 stop bits (with values 1 and 1)
     */
    localparam PACKET_DATA_SIZE = 8;

    localparam PACKET_SIZE      = 11;
    localparam PACKET_BITS      = $clog2(PACKET_SIZE + 1);

    reg  [PACKET_DATA_SIZE - 1:0] packet_data;

    wire [PACKET_SIZE - 1:0] packet = {2'b11, packet_data, 1'b0};
    reg  [PACKET_BITS - 1:0] packet_transmission_index;

    // Transmission, set the packet body and transmit
    reg transmission_running = 0;

    // Combinational logic for the transmission
    always @(*) begin
        if (transmission_running) begin
            uart = packet[packet_transmission_index];
        end else begin
            uart = 1; // High when idle
        end
    end

    assign ready = !transmission_running;

    reg [CLOCK_DIVIDER_BITS - 1:0] clock_divider_cycle_count_next = 0;
    reg [PACKET_DATA_SIZE - 1:0]   packet_data_next = 0;
    reg [PACKET_BITS - 1:0]        packet_transmission_index_next = 0;
    reg                            transmission_running_next = 0;

    always @(*) begin
        packet_data_next               = packet_data;
        transmission_running_next      = transmission_running;
        clock_divider_cycle_count_next = clock_divider_cycle_count;
        packet_transmission_index_next = packet_transmission_index;

        // Set the packet data, move to running mode, and clear the tracking registers to start a new transmission
        if (!transmission_running && valid) begin
            packet_data_next               = data;
            transmission_running_next      = 1;

            clock_divider_cycle_count_next = 0;
            packet_transmission_index_next = 0;
        end

        if (transmission_running) begin
            // Since the baud rate is considerably slower than the clock speed of the FPGA
            // While transmitting, we need to hold the transmitted value for a number of clock cycles dictated by the clock divider.
            if (clock_divider_cycle_count < CLOCK_DIVIDER_SIZE - 1) begin
                // Waiting for the current bit to be transmitted
                clock_divider_cycle_count_next = clock_divider_cycle_count + 1;
            end else begin
                // End of bit transmission, move onto the next bit.
                clock_divider_cycle_count_next = 0;

                // Handling packet end.
                if (packet_transmission_index < (PACKET_SIZE - 1)) begin
                    // If we're still transmitting the same packet, move to the next index.
                    packet_transmission_index_next = packet_transmission_index + 1;
                end else begin
                    // Otherwise, move out of running mode
                    transmission_running_next = 0;
                end
            end
        end
    end

    // Registers
    always @(posedge clock) begin
        if (reset) begin
            packet_data               <= 0;
            transmission_running      <= 0;
            clock_divider_cycle_count <= 0;
            packet_transmission_index <= 0;
        end else begin
            packet_data               <= packet_data_next;
            transmission_running      <= transmission_running_next;
            clock_divider_cycle_count <= clock_divider_cycle_count_next;
            packet_transmission_index <= packet_transmission_index_next;
        end
    end

endmodule