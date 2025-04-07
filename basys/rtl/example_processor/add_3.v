`default_nettype none
`timescale 1ns / 1ps

module add_3
(
    input  wire       clock,
    input  wire       reset,
    input  wire       enable,

    input  wire [7:0] in_data,
    input  wire       in_valid,
    input  wire       in_last,

    output wire [7:0] out_data,
    output wire       out_valid,
    output wire       out_last
);

    reg [7:0] addition_1;
    reg       addition_1_valid;
    reg       addition_1_last;

    reg [7:0] addition_1_next;
    reg       addition_1_valid_next;
    reg       addition_1_last_next;

    reg [7:0] addition_2;
    reg       addition_2_valid;
    reg       addition_2_last;

    reg [7:0] addition_2_next;
    reg       addition_2_valid_next;
    reg       addition_2_last_next;

    reg [7:0] addition_3;
    reg       addition_3_valid;
    reg       addition_3_last;

    reg [7:0] addition_3_next;
    reg       addition_3_valid_next;
    reg       addition_3_last_next;

    always @(*) begin
        addition_1_next = in_data + 1;
        addition_2_next = addition_1 + 1;
        addition_3_next = addition_2 + 1;

        addition_1_valid_next = 1;
        addition_2_valid_next = addition_1_valid;
        addition_3_valid_next = addition_2_valid;

        addition_1_last_next = in_last;
        addition_2_last_next = addition_1_last;
        addition_3_last_next = addition_2_last;
    end

    assign out_data  = addition_3;
    assign out_valid = addition_3_valid;
    assign out_last  = addition_3_last;

    always @(posedge clock) begin
        if (reset) begin
            addition_1 <= 0;
            addition_2 <= 0;
            addition_3 <= 0;

            addition_1_valid <= 0;
            addition_2_valid <= 0;
            addition_3_valid <= 0;

            addition_1_last <= 0;
            addition_2_last <= 0;
            addition_3_last <= 0;
        end else if (enable) begin
            addition_1 <= addition_1_next;
            addition_2 <= addition_2_next;
            addition_3 <= addition_3_next;

            addition_1_valid <= addition_1_valid_next;
            addition_2_valid <= addition_2_valid_next;
            addition_3_valid <= addition_3_valid_next;

            addition_1_last <= addition_1_last_next;
            addition_2_last <= addition_2_last_next;
            addition_3_last <= addition_3_last_next;
        end else begin
            addition_1 <= addition_1;
            addition_2 <= addition_2;
            addition_3 <= addition_3;

            addition_1_valid <= addition_1_valid;
            addition_2_valid <= addition_2_valid;
            addition_3_valid <= addition_3_valid;

            addition_1_last <= addition_1_last;
            addition_2_last <= addition_2_last;
            addition_3_last <= addition_3_last;
        end
    end

endmodule
