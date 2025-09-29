/* BITMAP TO TENSOR
 * ================
 */
module bitmap_to_tensor
#(
    parameter TDATA_WIDTH  = 256,
    parameter TUSER_WIDTH  = 128,

    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
)
(
    // Global Ports
    input                      axis_aclk,
    input                      axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0] bitmap_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0] bitmap_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0] bitmap_in_axis_tuser,
    input                      bitmap_in_axis_tvalid,
    output                     bitmap_in_axis_tready,
    input                      bitmap_in_axis_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] tensor_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0] tensor_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0] tensor_out_axis_tuser,
    output                     tensor_out_axis_tvalid,
    input                      tensor_out_axis_tready,
    output                     tensor_out_axis_tlast
);


    // STAGE 1: Reshape the bitmap from a (H x W x C) array to a (C x H x W)

    // Create output wires
    wire [TDATA_WIDTH - 1:0] reshaped_axis_tdata;
    wire [TKEEP_WIDTH - 1:0] reshaped_axis_tkeep;
    wire [TUSER_WIDTH - 1:0] reshaped_axis_tuser;
    wire                     reshaped_axis_tvalid;
    wire                     reshaped_axis_tready;
    wire                     reshaped_axis_tlast;

    // Instantiate the module
    bitmap_reshaper
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    reshape_bitmap
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_input_tdata(bitmap_in_axis_tdata),
        .axis_input_tkeep(bitmap_in_axis_tkeep),
        .axis_input_tuser(bitmap_in_axis_tuser),
        .axis_input_tvalid(bitmap_in_axis_tvalid),
        .axis_input_tready(bitmap_in_axis_tready),
        .axis_input_tlast(bitmap_in_axis_tlast),

        .axis_output_tdata(reshaped_axis_tdata),
        .axis_output_tkeep(reshaped_axis_tkeep),
        .axis_output_tuser(reshaped_axis_tuser),
        .axis_output_tvalid(reshaped_axis_tvalid),
        .axis_output_tready(reshaped_axis_tready),
        .axis_output_tlast(reshaped_axis_tlast)
    );  


    // STAGE 2: Scaling

    // Instantiate the module
    image_to_tensor_scaler
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    scale_bitmap
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_image_tdata(reshaped_axis_tdata),
        .axis_image_tkeep(reshaped_axis_tkeep),
        .axis_image_tuser(reshaped_axis_tuser),
        .axis_image_tvalid(reshaped_axis_tvalid),
        .axis_image_tready(reshaped_axis_tready),
        .axis_image_tlast(reshaped_axis_tlast),

        .axis_tensor_tdata(tensor_out_axis_tdata),
        .axis_tensor_tkeep(tensor_out_axis_tkeep),
        .axis_tensor_tuser(tensor_out_axis_tuser),
        .axis_tensor_tvalid(tensor_out_axis_tvalid),
        .axis_tensor_tready(tensor_out_axis_tready),
        .axis_tensor_tlast(tensor_out_axis_tlast)
    );

endmodule