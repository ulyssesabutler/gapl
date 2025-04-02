`default_nettype none
`timescale 1ns / 1ps

/* DEBOUNCER
 *
 * out starts with the value 0. If in maintains the same value for COUNT_MAX
 * clock cycles, update the out to reflect that new value.
 * 
 * https://github.com/Digilent/Basys-3-Keyboard/blob/master/src/hdl/debouncer.v
 */
module debouncer
#(
    parameter  COUNT_MAX  = 256,
    parameter  DEFAULT = 0
) (
    input wire clock,
    input wire in,
    output reg out = DEFAULT
);

    localparam COUNT_BITS = $clog2(COUNT_MAX + 1);

    reg [COUNT_BITS - 1:0] count;
    reg in_previous = 0;

    always@(posedge clock) begin
        if (in == in_previous) begin
            if (count == COUNT_MAX)
                out <= in;
            else
                count <= count + 1;
        end else begin
            count <= 0;
            in_previous <= in;
        end
    end
    
endmodule