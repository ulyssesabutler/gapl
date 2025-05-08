`default_nettype none
`timescale 1ns / 1ps

module arbiter_2_to_1
#(
    parameter DATA_WIDTH = 8
) (
    input  wire                    clock,
    input  wire                    reset,

    input  wire [DATA_WIDTH - 1:0] in1_data,
    input  wire                    in1_valid,

    input  wire [DATA_WIDTH - 1:0] in2_data,
    input  wire                    in2_valid,

    output wire                    in_ready,

    output reg  [DATA_WIDTH - 1:0] out_data,
    output reg                     out_valid,
    input  wire                    out_ready
);

    // Internal Buffer
    reg [DATA_WIDTH - 1:0] buffer_data;
    reg                    buffer_valid;

    // Ready as long as the buffer is empty and the output will be empty
    wire buffer_is_empty      = !buffer_valid;
    wire output_will_be_empty = !out_valid || out_ready;

    assign in_ready = buffer_is_empty && output_will_be_empty;

    // Refill the output
    reg  [DATA_WIDTH - 1:0] out_data_next;
    reg                     out_valid_next;

    // And buffer
    reg [DATA_WIDTH - 1:0] buffer_data_next;
    reg                    buffer_valid_next;

    always @(*) begin
        out_data_next  = out_data;
        out_valid_next = out_valid;

        buffer_data_next  = buffer_data;
        buffer_valid_next = buffer_valid;

        // If the buffer is empty and the output will be empty
        if (in_ready) begin
            out_data_next  = in1_data;
            out_valid_next = in1_valid;

            buffer_data_next  = in2_data;
            buffer_valid_next = in2_valid;
        end else if (out_ready & buffer_valid) begin
            out_data_next  = buffer_data;
            out_valid_next = buffer_valid;

            buffer_data_next  = 0;
            buffer_valid_next = 0;
        end
    end

    // Registers
    always @(posedge clock) begin
        if (reset) begin
            out_data     <= 0;
            out_valid    <= 0;

            buffer_data  <= 0;
            buffer_valid <= 0;
        end else begin
            out_data     <= out_data_next;
            out_valid    <= out_valid_next;

            buffer_data  <= buffer_data_next;
            buffer_valid <= buffer_valid_next;
        end
    end

endmodule