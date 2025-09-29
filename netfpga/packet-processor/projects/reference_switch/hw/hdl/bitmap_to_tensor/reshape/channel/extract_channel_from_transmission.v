module extract_channel_from_transmission
#(
    parameter  TDATA_WIDTH         = 256,

    parameter  CHANNEL_COUNT      = 3,
    parameter  CHANNEL_OFFSET     = 0,

    parameter  ITEM_WIDTH         = 8,
    localparam ITEM_COUNT         = TDATA_WIDTH / ITEM_WIDTH,
    localparam CHANNEL_COUNT_BITS = $clog2(CHANNEL_COUNT)
)
(
    input  [TDATA_WIDTH - 1:0] full_rgb,
    output [TDATA_WIDTH - 1:0] single_channel
);

    genvar item_index;

    generate
        for (item_index = 0; item_index < ITEM_COUNT; item_index = item_index + 1) begin
            if ((item_index * CHANNEL_COUNT + CHANNEL_OFFSET) < ITEM_COUNT) begin
                assign single_channel[(item_index + 1) * ITEM_WIDTH - 1:item_index * ITEM_WIDTH] = full_rgb[(item_index * CHANNEL_COUNT + CHANNEL_OFFSET + 1) * ITEM_WIDTH - 1:(item_index * CHANNEL_COUNT + CHANNEL_OFFSET) * ITEM_WIDTH];
            end else begin
                assign single_channel[(item_index + 1) * ITEM_WIDTH - 1:item_index * ITEM_WIDTH] = 0;
            end
        end
    endgenerate

endmodule