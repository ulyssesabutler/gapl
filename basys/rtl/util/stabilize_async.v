`timescale 1ns / 1ps

module stabilize_async
#(
    parameter  DEFAULT = 0
) (
    input  wire clock,
    input  wire reset,
    input  wire async_input,
    output wire sync_output
);

    reg metastable = DEFAULT;
    reg stable     = DEFAULT;

    always @(posedge clock) begin
        if (reset) begin
            metastable <= DEFAULT;
            stable     <= DEFAULT;
        end else begin
            metastable <= async_input;
            stable     <= metastable;
        end
    end

    assign sync_output = stable;

endmodule