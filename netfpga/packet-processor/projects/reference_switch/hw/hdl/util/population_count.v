module population_count
#(
    parameter  IN_WIDTH   = 256,
    localparam WIDTH_BITS = $clog2(IN_WIDTH + 1)
) (
    input      [IN_WIDTH - 1:0]   in,
    output reg [WIDTH_BITS - 1:0] population
);

    integer i;

    always @(in) begin
        population = 0;
        for (i = 0; i < IN_WIDTH; i = i + 1) population = population + in[i];
    end

endmodule