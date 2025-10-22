/* INFERENCE REQUEST PREPROCESSOR
 * ==============================
 * The input to this module will be a stream of network packets that are parts of inference requests.
 * We want to take those packets and process them so the request can be sent directly to the GPU.
 * - Decoding: Converting the JPEG image to a bitmap
 * - Cropping: Removing the edges
 * - Resizing: Scaling the image to the correct size
 * - Tensor Conversion: Convert the bitmap into a neural network input, in the form of a tensor
 */
module packet_processor
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    localparam MAC_ADDRESS_WIDTH      = 48,
    localparam IP_ADDRESS_WIDTH       = 32,
    localparam IP_LENGTH_WIDTH        = 16,
    localparam IP_ID_WIDTH            = 16,
    localparam PORT_WIDTH             = 16,
    localparam UDP_LENGTH_WIDTH       = 16,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8
)
(
    // Global Ports
    input                      axis_aclk,
    input                      axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0] packet_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0] packet_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0] packet_in_axis_tuser,
    input                      packet_in_axis_tvalid,
    output                     packet_in_axis_tready,
    input                      packet_in_axis_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] packet_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0] packet_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0] packet_out_axis_tuser,
    output                     packet_out_axis_tvalid,
    input                      packet_out_axis_tready,
    output                     packet_out_axis_tlast
);

    wire [TKEEP_WIDTH - 1:0] back_mask;
    wire [TKEEP_WIDTH - 1:0] body_mask;

    axis_mask_back
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH),
        .BYTES_DROPPED(14 + 20 + 8)
    )
    mask_generator
    (
        .clock(axis_aclk),
        .reset_n(axis_resetn),

        .in_tdata(packet_in_axis_tdata),
        .in_tkeep(packet_in_axis_tkeep),
        .in_tuser(packet_in_axis_tuser),
        .in_tvalid(packet_in_axis_tvalid),
        .in_tready(packet_in_axis_tready),
        .in_tlast(packet_in_axis_tlast),

        .mask(back_mask)
    );

    assign body_mask = packet_in_axis_tkeep & back_mask;


    gapl_wrapper
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    dut
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .packet_body_in_axis_tdata(packet_in_axis_tdata),
        .packet_body_in_axis_tkeep(body_mask),
        .packet_body_in_axis_tuser(packet_in_axis_tuser),
        .packet_body_in_axis_tvalid(packet_in_axis_tvalid),
        .packet_body_in_axis_tready(packet_in_axis_tready),
        .packet_body_in_axis_tlast(packet_in_axis_tlast),

        .packet_body_out_axis_tdata(packet_out_axis_tdata),
        .packet_body_out_axis_tkeep(packet_out_axis_tkeep),
        .packet_body_out_axis_tuser(packet_out_axis_tuser),
        .packet_body_out_axis_tvalid(packet_out_axis_tvalid),
        .packet_body_out_axis_tready(packet_out_axis_tready),
        .packet_body_out_axis_tlast(packet_out_axis_tlast)
    );

endmodule