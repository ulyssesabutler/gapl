module bitmap_reshaper
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
    input [TDATA_WIDTH - 1:0]  axis_input_tdata,
    input [TKEEP_WIDTH - 1:0]  axis_input_tkeep,
    input [TUSER_WIDTH - 1:0]  axis_input_tuser,
    input                      axis_input_tvalid,
    output                     axis_input_tready,
    input                      axis_input_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] axis_output_tdata,
    output [TKEEP_WIDTH - 1:0] axis_output_tkeep,
    output [TUSER_WIDTH - 1:0] axis_output_tuser,
    output                     axis_output_tvalid,
    input                      axis_output_tready,
    output                     axis_output_tlast
);  


    // INTERNAL CONNECTIVITY
    wire [TDATA_WIDTH - 1:0] axis_full_rgb_r_tdata;
    wire [TKEEP_WIDTH - 1:0] axis_full_rgb_r_tkeep;
    wire [TUSER_WIDTH - 1:0] axis_full_rgb_r_tuser;
    wire                     axis_full_rgb_r_tvalid;
    wire                     axis_full_rgb_r_tready;
    wire                     axis_full_rgb_r_tlast;

    wire [TDATA_WIDTH - 1:0] axis_full_rgb_g_tdata;
    wire [TKEEP_WIDTH - 1:0] axis_full_rgb_g_tkeep;
    wire [TUSER_WIDTH - 1:0] axis_full_rgb_g_tuser;
    wire                     axis_full_rgb_g_tvalid;
    wire                     axis_full_rgb_g_tready;
    wire                     axis_full_rgb_g_tlast;

    wire [TDATA_WIDTH - 1:0] axis_full_rgb_b_tdata;
    wire [TKEEP_WIDTH - 1:0] axis_full_rgb_b_tkeep;
    wire [TUSER_WIDTH - 1:0] axis_full_rgb_b_tuser;
    wire                     axis_full_rgb_b_tvalid;
    wire                     axis_full_rgb_b_tready;
    wire                     axis_full_rgb_b_tlast;

    wire [TDATA_WIDTH - 1:0] axis_single_channel_r_tdata;
    wire [TKEEP_WIDTH - 1:0] axis_single_channel_r_tkeep;
    wire [TUSER_WIDTH - 1:0] axis_single_channel_r_tuser;
    wire                     axis_single_channel_r_tvalid;
    wire                     axis_single_channel_r_tready;
    wire                     axis_single_channel_r_tlast;

    wire [TDATA_WIDTH - 1:0] axis_single_channel_g_tdata;
    wire [TKEEP_WIDTH - 1:0] axis_single_channel_g_tkeep;
    wire [TUSER_WIDTH - 1:0] axis_single_channel_g_tuser;
    wire                     axis_single_channel_g_tvalid;
    wire                     axis_single_channel_g_tready;
    wire                     axis_single_channel_g_tlast;

    wire [TDATA_WIDTH - 1:0] axis_single_channel_b_tdata;
    wire [TKEEP_WIDTH - 1:0] axis_single_channel_b_tkeep;
    wire [TUSER_WIDTH - 1:0] axis_single_channel_b_tuser;
    wire                     axis_single_channel_b_tvalid;
    wire                     axis_single_channel_b_tready;
    wire                     axis_single_channel_b_tlast;


    // MANIFOLD
    axis_1_to_3_manifold rbg_manifold
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_input_tdata(axis_input_tdata),
        .axis_input_tkeep(axis_input_tkeep),
        .axis_input_tuser(axis_input_tuser),
        .axis_input_tvalid(axis_input_tvalid),
        .axis_input_tready(axis_input_tready),
        .axis_input_tlast(axis_input_tlast),

        .axis_output_0_tdata(axis_full_rgb_r_tdata),
        .axis_output_0_tkeep(axis_full_rgb_r_tkeep),
        .axis_output_0_tuser(axis_full_rgb_r_tuser),
        .axis_output_0_tvalid(axis_full_rgb_r_tvalid),
        .axis_output_0_tready(axis_full_rgb_r_tready),
        .axis_output_0_tlast(axis_full_rgb_r_tlast),

        .axis_output_1_tdata(axis_full_rgb_g_tdata),
        .axis_output_1_tkeep(axis_full_rgb_g_tkeep),
        .axis_output_1_tuser(axis_full_rgb_g_tuser),
        .axis_output_1_tvalid(axis_full_rgb_g_tvalid),
        .axis_output_1_tready(axis_full_rgb_g_tready),
        .axis_output_1_tlast(axis_full_rgb_g_tlast),

        .axis_output_2_tdata(axis_full_rgb_b_tdata),
        .axis_output_2_tkeep(axis_full_rgb_b_tkeep),
        .axis_output_2_tuser(axis_full_rgb_b_tuser),
        .axis_output_2_tvalid(axis_full_rgb_b_tvalid),
        .axis_output_2_tready(axis_full_rgb_b_tready),
        .axis_output_2_tlast(axis_full_rgb_b_tlast)
    );


    // COLOR CHANNELS
    color_channel #( .CHANNEL_OFFSET(0) ) red_channel
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .full_rgb_tdata(axis_full_rgb_r_tdata),
        .full_rgb_tkeep(axis_full_rgb_r_tkeep),
        .full_rgb_tuser(axis_full_rgb_r_tuser),
        .full_rgb_tvalid(axis_full_rgb_r_tvalid),
        .full_rgb_tready(axis_full_rgb_r_tready),
        .full_rgb_tlast(axis_full_rgb_r_tlast),

        .single_channel_tdata(axis_single_channel_r_tdata),
        .single_channel_tkeep(axis_single_channel_r_tkeep),
        .single_channel_tuser(axis_single_channel_r_tuser),
        .single_channel_tvalid(axis_single_channel_r_tvalid),
        .single_channel_tready(axis_single_channel_r_tready),
        .single_channel_tlast(axis_single_channel_r_tlast)
    );

    color_channel #( .CHANNEL_OFFSET(1) ) green_channel
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .full_rgb_tdata(axis_full_rgb_g_tdata),
        .full_rgb_tkeep(axis_full_rgb_g_tkeep),
        .full_rgb_tuser(axis_full_rgb_g_tuser),
        .full_rgb_tvalid(axis_full_rgb_g_tvalid),
        .full_rgb_tready(axis_full_rgb_g_tready),
        .full_rgb_tlast(axis_full_rgb_g_tlast),

        .single_channel_tdata(axis_single_channel_g_tdata),
        .single_channel_tkeep(axis_single_channel_g_tkeep),
        .single_channel_tuser(axis_single_channel_g_tuser),
        .single_channel_tvalid(axis_single_channel_g_tvalid),
        .single_channel_tready(axis_single_channel_g_tready),
        .single_channel_tlast(axis_single_channel_g_tlast)
    );

    color_channel #( .CHANNEL_OFFSET(2) ) blue_channel
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .full_rgb_tdata(axis_full_rgb_b_tdata),
        .full_rgb_tkeep(axis_full_rgb_b_tkeep),
        .full_rgb_tuser(axis_full_rgb_b_tuser),
        .full_rgb_tvalid(axis_full_rgb_b_tvalid),
        .full_rgb_tready(axis_full_rgb_b_tready),
        .full_rgb_tlast(axis_full_rgb_b_tlast),

        .single_channel_tdata(axis_single_channel_b_tdata),
        .single_channel_tkeep(axis_single_channel_b_tkeep),
        .single_channel_tuser(axis_single_channel_b_tuser),
        .single_channel_tvalid(axis_single_channel_b_tvalid),
        .single_channel_tready(axis_single_channel_b_tready),
        .single_channel_tlast(axis_single_channel_b_tlast)
    );


    // ARBITER
    axis_3_to_1_arbiter rgb_arbiter
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_input_0_tdata(axis_single_channel_r_tdata),
        .axis_input_0_tkeep(axis_single_channel_r_tkeep),
        .axis_input_0_tuser(axis_single_channel_r_tuser),
        .axis_input_0_tvalid(axis_single_channel_r_tvalid),
        .axis_input_0_tready(axis_single_channel_r_tready),
        .axis_input_0_tlast(axis_single_channel_r_tlast),

        .axis_input_1_tdata(axis_single_channel_g_tdata),
        .axis_input_1_tkeep(axis_single_channel_g_tkeep),
        .axis_input_1_tuser(axis_single_channel_g_tuser),
        .axis_input_1_tvalid(axis_single_channel_g_tvalid),
        .axis_input_1_tready(axis_single_channel_g_tready),
        .axis_input_1_tlast(axis_single_channel_g_tlast),

        .axis_input_2_tdata(axis_single_channel_b_tdata),
        .axis_input_2_tkeep(axis_single_channel_b_tkeep),
        .axis_input_2_tuser(axis_single_channel_b_tuser),
        .axis_input_2_tvalid(axis_single_channel_b_tvalid),
        .axis_input_2_tready(axis_single_channel_b_tready),
        .axis_input_2_tlast(axis_single_channel_b_tlast),

        .axis_output_tdata(axis_output_tdata),
        .axis_output_tkeep(axis_output_tkeep),
        .axis_output_tuser(axis_output_tuser),
        .axis_output_tvalid(axis_output_tvalid),
        .axis_output_tready(axis_output_tready),
        .axis_output_tlast(axis_output_tlast)
    );

endmodule