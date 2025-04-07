`default_nettype none

module packet_constructor
(
    input  wire       clock,
    input  wire       reset,

    input  wire [7:0] packet_data,
    input  wire       packet_valid,
    output wire       packet_ready,
    input  wire       packet_last,

    output wire [7:0] uart_data,
    output wire       uart_valid,
    input  wire       uart_ready
);

    // Arbiter
    reg  [7:0] arbiter_in1_data;
    reg        arbiter_in1_valid;

    wire [7:0] arbiter_in2_data = 8'h0; // Sentinel value
    reg        arbiter_in2_valid;

    wire       arbiter_ready;

    arbiter_2_to_1 #( .DATA_WIDTH(8) ) arbiter
    (
        .clock(clock),
        .reset(reset),

        .in1_data(arbiter_in1_data),
        .in1_valid(arbiter_in1_valid),

        .in2_data(arbiter_in2_data),
        .in2_valid(arbiter_in2_valid),

        .in_ready(arbiter_ready),

        .out_data(uart_data),
        .out_valid(uart_valid),
        .out_ready(uart_ready)
    );

    assign packet_ready = arbiter_ready;

    // Fill arbiter
    reg  [7:0] arbiter_in1_data_next;
    reg        arbiter_in1_valid_next;

    reg        arbiter_in2_valid_next;

    always @(*) begin
        arbiter_in1_data_next  = 0;
        arbiter_in1_valid_next = 0;

        arbiter_in2_valid_next = 0;

        if (packet_valid & arbiter_ready) begin
            arbiter_in1_data_next  = packet_data;
            arbiter_in1_valid_next = packet_valid;

            if (packet_last) begin
                arbiter_in2_valid_next = 1;
            end else begin
                arbiter_in2_valid_next = 0;
            end
        end
    end

    // Registers
    always @(posedge clock) begin
        if (reset) begin
            arbiter_in1_data  <= 0;
            arbiter_in1_valid <= 0;

            arbiter_in2_valid <= 0;
        end else begin
            arbiter_in1_data  <= arbiter_in1_data_next;
            arbiter_in1_valid <= arbiter_in1_valid_next;

            arbiter_in2_valid <= arbiter_in2_valid_next;
        end
    end

endmodule