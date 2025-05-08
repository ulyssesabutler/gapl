`default_nettype none
`timescale 1ns / 1ps

module timer
#(
    parameter TIMER_SIZE = 32
) (
    input  wire                    clock,
    input  wire                    reset,

    input  wire                    enable,
    output reg  [TIMER_SIZE - 1:0] clock_cycles
);

    reg [TIMER_SIZE - 1:0] clock_cycles_next;

    always @(*) begin
        if (enable) begin
            clock_cycles_next <= clock_cycles + 1;
        end else begin
            clock_cycles_next <= clock_cycles;
        end
    end

    always @(posedge clock) begin
        if (reset) begin
            clock_cycles <= 0;
        end else begin
            clock_cycles <= clock_cycles_next;
        end
    end

endmodule
