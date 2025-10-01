module ones_complement_addition
#(
    parameter WIDTH = 16
)
(
    input  [WIDTH - 1:0] operand_1,
    input  [WIDTH - 1:0] operand_2,
    output [WIDTH - 1:0] sum
);
    wire [WIDTH:0] raw_sum = {1'b0, operand_1} + {1'b0, operand_2};
    assign sum = raw_sum[WIDTH-1:0] + raw_sum[WIDTH];
endmodule