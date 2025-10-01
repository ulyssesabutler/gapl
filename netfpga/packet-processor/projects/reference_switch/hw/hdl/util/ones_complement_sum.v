module ones_complement_sum
#(
    parameter WIDTH = 16,
    parameter OPERAND_COUNT = 16
)
(
    input  [WIDTH * OPERAND_COUNT - 1:0] operands,
    output [WIDTH - 1:0]                 sum
);

    localparam W = WIDTH;
    localparam N = OPERAND_COUNT;

    // Running accumulator after i additions (accum[0] is just operand 0)
    wire [W-1:0] accum [0:N-1];

    // Seed accumulator with the first operand
    assign accum[0] = operands[0*W +: W];

    genvar i;
    generate
        for (i = 1; i < N; i = i + 1) begin : gen_add
            ones_complement_addition #(.WIDTH(W)) add_i (
                .operand_1(accum[i-1]),
                .operand_2(operands[i*W +: W]),
                .sum      (accum[i])
            );
        end
    endgenerate

    assign sum = accum[N-1];

endmodule