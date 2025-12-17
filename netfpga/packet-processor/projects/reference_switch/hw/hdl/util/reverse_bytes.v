module reverse_bytes #(
    parameter BYTES = 4
) (
    input  wire [(BYTES * 8) - 1:0] in,
    output wire [(BYTES * 8) - 1:0] out
);

    genvar i;
    generate
        for (i = 0; i < BYTES; i = i + 1) begin : gen_rev
            assign out[(i * 8) +: 8] = in[(BYTES - 1 - i) * 8 +: 8];
        end
    endgenerate

endmodule