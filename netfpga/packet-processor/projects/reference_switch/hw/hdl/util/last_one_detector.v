module last_one_detector
#(parameter INPUT_SIZE = 32)
(
    input  wire [INPUT_SIZE - 1:0] in,
    output wire [INPUT_SIZE - 1:0] one_hot
);

    genvar i;

    generate
        for (i = 0; i < INPUT_SIZE; i = i + 1) begin
            assign one_hot[i] = in[i] & !(in >> (i + 1));
        end
    endgenerate

endmodule