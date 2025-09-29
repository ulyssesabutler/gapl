module reverse_byte_order
#(
    parameter BYTE_COUNT = 2
)
(
    input  [(BYTE_COUNT * 8) - 1:0] input_number,
    output [(BYTE_COUNT * 8) - 1:0] output_number
);

    genvar i;

    generate
        for (i = 0; i < BYTE_COUNT; i = i + 1) begin
            assign output_number[((i + 1) * 8) - 1:i * 8] = input_number[((BYTE_COUNT - i) * 8) - 1:(BYTE_COUNT - i - 1) * 8];
        end
    endgenerate

endmodule