module color_channel
#(
    parameter  TDATA_WIDTH         = 256,
    parameter  TKEEP_WIDTH         = TDATA_WIDTH / 8,
    parameter  TUSER_WIDTH         = 128,

    parameter  CHANNEL_COUNT      = 3,
    parameter  CHANNEL_OFFSET     = 0,

    parameter  ITEM_WIDTH         = 8,
    localparam ITEM_COUNT         = TDATA_WIDTH / ITEM_WIDTH,
    localparam CHANNEL_COUNT_BITS = $clog2(CHANNEL_COUNT)
)
(
    // Global Ports
    input                      axis_aclk,
    input                      axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0] full_rgb_tdata,
    input  [TKEEP_WIDTH - 1:0] full_rgb_tkeep,
    input  [TUSER_WIDTH - 1:0] full_rgb_tuser,
    input                      full_rgb_tvalid,
    output                     full_rgb_tready,
    input                      full_rgb_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] single_channel_tdata,
    output [TKEEP_WIDTH - 1:0] single_channel_tkeep,
    output [TUSER_WIDTH - 1:0] single_channel_tuser,
    output                     single_channel_tvalid,
    input                      single_channel_tready,
    output                     single_channel_tlast
);

    // Internal Connectivity
    wire [TDATA_WIDTH - 1:0] extracted_color_tdata;
    wire [TKEEP_WIDTH - 1:0] extracted_color_tkeep;
    wire [TUSER_WIDTH - 1:0] extracted_color_tuser;
    wire                     extracted_color_tvalid;
    wire                     extracted_color_tready;
    wire                     extracted_color_tlast;

    // Extractor
    extract_channel_from_stream
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TKEEP_WIDTH(TKEEP_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH),

        .CHANNEL_COUNT(CHANNEL_COUNT),
        .CHANNEL_OFFSET(CHANNEL_OFFSET),

        .ITEM_WIDTH(ITEM_WIDTH)
    )
    extract_color_channel_from_stream
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .full_rgb_tdata(full_rgb_tdata),
        .full_rgb_tkeep(full_rgb_tkeep),
        .full_rgb_tuser(full_rgb_tuser),
        .full_rgb_tvalid(full_rgb_tvalid),
        .full_rgb_tready(full_rgb_tready),
        .full_rgb_tlast(full_rgb_tlast),

        .single_channel_tdata(extracted_color_tdata),
        .single_channel_tkeep(extracted_color_tkeep),
        .single_channel_tuser(extracted_color_tuser),
        .single_channel_tvalid(extracted_color_tvalid),
        .single_channel_tready(extracted_color_tready),
        .single_channel_tlast(extracted_color_tlast)
    );

    // Buffer
    wire output_queue_nearly_full;
    wire output_queue_empty;

    wire write_to_output_queue = extracted_color_tready & extracted_color_tvalid;
    assign extracted_color_tready = ~output_queue_nearly_full;

    wire read_from_output_queue = single_channel_tready & single_channel_tvalid;
    assign single_channel_tvalid = ~output_queue_empty; 

    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH+TUSER_WIDTH+TKEEP_WIDTH+1),
        .MAX_DEPTH_BITS(10)
    )
    output_fifo
    (
        .din         ({extracted_color_tdata, extracted_color_tkeep, extracted_color_tuser, extracted_color_tlast}),
        .wr_en       (write_to_output_queue),
        .rd_en       (read_from_output_queue),
        .dout        ({single_channel_tdata, single_channel_tkeep, single_channel_tuser, single_channel_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (output_queue_nearly_full),
        .empty       (output_queue_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

endmodule