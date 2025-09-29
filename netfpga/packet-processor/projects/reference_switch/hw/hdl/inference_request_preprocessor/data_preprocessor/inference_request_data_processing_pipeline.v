module inference_request_data_processing_pipeline
#(
    parameter TDATA_WIDTH  = 256,
    parameter TUSER_WIDTH  = 128,

    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
)
(
    // Global Ports
    input                                axis_aclk,
    input                                axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0]           data_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0]           data_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0]           data_in_axis_tuser,
    input                                data_in_axis_tvalid,
    output                               data_in_axis_tready,
    input                                data_in_axis_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0]           data_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0]           data_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0]           data_out_axis_tuser,
    output                               data_out_axis_tvalid,
    input                                data_out_axis_tready,
    output                               data_out_axis_tlast
);

    // STAGE 1: Decode image

    // Create output wires
    wire [15:0]              bitmap_height;
    wire [15:0]              bitmap_width;

    wire [TDATA_WIDTH - 1:0] bitmap_axis_tdata;
    wire [TKEEP_WIDTH - 1:0] bitmap_axis_tkeep;
    wire [TUSER_WIDTH - 1:0] bitmap_axis_tuser;
    wire                     bitmap_axis_tvalid;
    wire                     bitmap_axis_tready;
    wire                     bitmap_axis_tlast;

    jpeg_to_bitmap
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    jpeg_to_bitmap_converter
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .jpeg_axis_tdata(data_in_axis_tdata),
        .jpeg_axis_tkeep(data_in_axis_tkeep),
        .jpeg_axis_tuser(data_in_axis_tuser),
        .jpeg_axis_tvalid(data_in_axis_tvalid),
        .jpeg_axis_tready(data_in_axis_tready),
        .jpeg_axis_tlast(data_in_axis_tlast),

        .bitmap_height(bitmap_height),
        .bitmap_width(bitmap_width),

        .bitmap_axis_tdata(bitmap_axis_tdata),
        .bitmap_axis_tkeep(bitmap_axis_tkeep),
        .bitmap_axis_tuser(bitmap_axis_tuser),
        .bitmap_axis_tvalid(bitmap_axis_tvalid),
        .bitmap_axis_tready(bitmap_axis_tready),
        .bitmap_axis_tlast(bitmap_axis_tlast)
    );


    // STAGE 2: Convert that bitmap to a tensor
    bitmap_to_tensor
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    bitmap_to_tensor_converter
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .bitmap_in_axis_tdata(bitmap_axis_tdata),
        .bitmap_in_axis_tkeep(bitmap_axis_tkeep),
        .bitmap_in_axis_tuser(bitmap_axis_tuser),
        .bitmap_in_axis_tvalid(bitmap_axis_tvalid),
        .bitmap_in_axis_tready(bitmap_axis_tready),
        .bitmap_in_axis_tlast(bitmap_axis_tlast),

        .tensor_out_axis_tdata(data_out_axis_tdata),
        .tensor_out_axis_tkeep(data_out_axis_tkeep),
        .tensor_out_axis_tuser(data_out_axis_tuser),
        .tensor_out_axis_tvalid(data_out_axis_tvalid),
        .tensor_out_axis_tready(data_out_axis_tready),
        .tensor_out_axis_tlast(data_out_axis_tlast)
    );

endmodule