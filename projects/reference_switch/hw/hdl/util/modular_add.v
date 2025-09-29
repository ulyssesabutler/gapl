module modular_add
#(
    parameter INT_WIDTH = 32,
    parameter MOD       = 256
)
(
    input  [INT_WIDTH - 1:0] in_a,
    input  [INT_WIDTH - 1:0] in_b,
    output [INT_WIDTH - 1:0] result
);

    assign result = ((in_a + in_b) < MOD) ? in_a + in_b : in_a + in_b - MOD;

endmodule