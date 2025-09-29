module byte_to_bit_mask
#(parameter BYTE_MASK_WIDTH = 32)
(
    input [BYTE_MASK_WIDTH - 1:0] byte_mask,
    output reg [(BYTE_MASK_WIDTH * 8) - 1:0] bit_mask
);

    integer i;
    
    always @(*) begin
        for (i = 0; i < BYTE_MASK_WIDTH; i = i + 1) begin
            bit_mask[i*8 +: 8] = {8{byte_mask[i]}};
        end
    end

endmodule
