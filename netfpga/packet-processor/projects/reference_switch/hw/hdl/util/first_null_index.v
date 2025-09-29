module first_null_index
#(parameter DATA_WIDTH = 32)
(
    input      [DATA_WIDTH - 1:0] data,
    output reg [31:0]             index
);

    reg signed [31:0] i;
    reg               found_non_null_index;

    always @(*) begin
        found_non_null_index = 0;
        index                = DATA_WIDTH;
        
        for (i = DATA_WIDTH - 1; i >= 0; i = i - 1) begin
            if (data[i])
                found_non_null_index = 1;
            else if (~found_non_null_index)
                index = i;
        end
    end

endmodule
