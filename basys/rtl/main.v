`default_nettype none
`timescale 100fs/100fs

module main
(
    input  wire       clock,

    input  wire       button_center,
    input  wire       button_right,
    input  wire       button_left,

    output wire [7:0] leds,
    output wire [7:0] status,
    input  wire [7:0] switches,

    input  wire       uart_receive,
    output wire       uart_transmit
);

    wire stable_button_center;
    wire stable_button_left;
    wire stable_button_right;

    debouncer debounce_button_center
    (
        .clock(clock),
        .in(button_center),
        .out(stable_button_center)
    );

    debouncer debounce_button_right
    (
        .clock(clock),
        .in(button_right),
        .out(stable_button_right)
    );

    debouncer debounce_button_left
    (
        .clock(clock),
        .in(button_left),
        .out(stable_button_left)
    );

    wire reset              = stable_button_center;
    wire send_next_byte     = stable_button_right;
    wire receive_next_byte  = stable_button_left;

    wire [7:0] received_data;
    wire       received_valid;

    uart_controller uart
    (
        .clock(clock),
        .reset(reset),

        .receive_uart(uart_receive),
        .transmit_uart(uart_transmit),

        .transmit_data(switches),
        .transmit_valid(stable_button_right),

        .receive_data(received_data),
        .receive_valid(received_valid),
        .receive_ready(1)
    );

    reg  [7:0] led_registers;
    reg  [7:0] led_registers_next;

    assign leds = led_registers;

    reg [7:0] received_packet_count;
    reg [7:0] received_packet_count_next;

    assign status = received_packet_count;
    always @(*) begin
        led_registers_next         = led_registers;
        received_packet_count_next = received_packet_count;

        if (received_valid) begin
            led_registers_next         = received_data;
            received_packet_count_next = received_packet_count + 1;
        end
    end

    always @(posedge clock) begin
        if (reset) begin
            led_registers <= 0;
            received_packet_count <= 0;
        end else begin
            led_registers <= led_registers_next;
            received_packet_count <= received_packet_count_next;
        end
    end

endmodule
