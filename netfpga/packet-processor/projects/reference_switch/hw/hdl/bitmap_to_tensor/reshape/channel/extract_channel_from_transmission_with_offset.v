module extract_channel_from_transmission_with_offset
#(
    parameter  TDATA_WIDTH         = 256,

    parameter  CHANNEL_COUNT      = 3,

    parameter  ITEM_WIDTH         = 8,
    localparam ITEM_COUNT         = TDATA_WIDTH / ITEM_WIDTH,
    localparam CHANNEL_COUNT_BITS = $clog2(CHANNEL_COUNT)
)
(
    input  [TDATA_WIDTH - 1:0]        full_rgb,
    input  [CHANNEL_COUNT_BITS - 1:0] channel_offset,
    output [TDATA_WIDTH - 1:0]        single_channel
);

    wire [TDATA_WIDTH - 1:0] single_channel_with_offset [CHANNEL_COUNT - 1:0];

    genvar i;

    generate
        for (i = 0; i < CHANNEL_COUNT; i = i + 1) begin
            extract_channel_from_transmission
            #(
                .TDATA_WIDTH(TDATA_WIDTH),
                .ITEM_WIDTH(ITEM_WIDTH),
                .CHANNEL_OFFSET(i)
            )
            extract_channel
            (
                .full_rgb(full_rgb),
                .single_channel(single_channel_with_offset[i])
            );
        end
    endgenerate

    assign single_channel = single_channel_with_offset[channel_offset];

endmodule