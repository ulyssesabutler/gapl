module image_to_tensor_scaler
#(
    parameter TDATA_WIDTH        = 256,
    parameter TUSER_WIDTH        = 128,

    localparam TKEEP_WIDTH       = TDATA_WIDTH / 8,
    
    localparam SMALL_TDATA_WIDTH = TDATA_WIDTH / 4,
    localparam SMALL_TKEEP_WIDTH = SMALL_TDATA_WIDTH / 8
)
(
    // Global Ports
    input                      axis_aclk,
    input                      axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0] axis_image_tdata,
    input  [TKEEP_WIDTH - 1:0] axis_image_tkeep,
    input  [TUSER_WIDTH - 1:0] axis_image_tuser,
    input                      axis_image_tvalid,
    output                     axis_image_tready,
    input                      axis_image_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] axis_tensor_tdata,
    output [TKEEP_WIDTH - 1:0] axis_tensor_tkeep,
    output [TUSER_WIDTH - 1:0] axis_tensor_tuser,
    output                     axis_tensor_tvalid,
    input                      axis_tensor_tready,
    output                     axis_tensor_tlast
);

    wire [SMALL_TDATA_WIDTH - 1:0] axis_image_small_0_tdata;
    wire [SMALL_TKEEP_WIDTH - 1:0] axis_image_small_0_tkeep;
    wire [TUSER_WIDTH - 1:0]       axis_image_small_0_tuser;
    wire                           axis_image_small_0_tvalid;
    wire                           axis_image_small_0_tready;
    wire                           axis_image_small_0_tlast;

    wire [SMALL_TDATA_WIDTH - 1:0] axis_image_small_1_tdata;
    wire [SMALL_TKEEP_WIDTH - 1:0] axis_image_small_1_tkeep;
    wire [TUSER_WIDTH - 1:0]       axis_image_small_1_tuser;
    wire                           axis_image_small_1_tvalid;
    wire                           axis_image_small_1_tready;
    wire                           axis_image_small_1_tlast;

    wire [SMALL_TDATA_WIDTH - 1:0] axis_image_small_2_tdata;
    wire [SMALL_TKEEP_WIDTH - 1:0] axis_image_small_2_tkeep;
    wire [TUSER_WIDTH - 1:0]       axis_image_small_2_tuser;
    wire                           axis_image_small_2_tvalid;
    wire                           axis_image_small_2_tready;
    wire                           axis_image_small_2_tlast;

    wire [SMALL_TDATA_WIDTH - 1:0] axis_image_small_3_tdata;
    wire [SMALL_TKEEP_WIDTH - 1:0] axis_image_small_3_tkeep;
    wire [TUSER_WIDTH - 1:0]       axis_image_small_3_tuser;
    wire                           axis_image_small_3_tvalid;
    wire                           axis_image_small_3_tready;
    wire                           axis_image_small_3_tlast;

    // Split
    axis_transmission_splitter transmission_splitter
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_original_tdata(axis_image_tdata),
        .axis_original_tkeep(axis_image_tkeep),
        .axis_original_tuser(axis_image_tuser),
        .axis_original_tvalid(axis_image_tvalid),
        .axis_original_tready(axis_image_tready),
        .axis_original_tlast(axis_image_tlast),

        .axis_resize_0_tdata(axis_image_small_0_tdata),
        .axis_resize_0_tkeep(axis_image_small_0_tkeep),
        .axis_resize_0_tuser(axis_image_small_0_tuser),
        .axis_resize_0_tvalid(axis_image_small_0_tvalid),
        .axis_resize_0_tready(axis_image_small_0_tready),
        .axis_resize_0_tlast(axis_image_small_0_tlast),

        .axis_resize_1_tdata(axis_image_small_1_tdata),
        .axis_resize_1_tkeep(axis_image_small_1_tkeep),
        .axis_resize_1_tuser(axis_image_small_1_tuser),
        .axis_resize_1_tvalid(axis_image_small_1_tvalid),
        .axis_resize_1_tready(axis_image_small_1_tready),
        .axis_resize_1_tlast(axis_image_small_1_tlast),

        .axis_resize_2_tdata(axis_image_small_2_tdata),
        .axis_resize_2_tkeep(axis_image_small_2_tkeep),
        .axis_resize_2_tuser(axis_image_small_2_tuser),
        .axis_resize_2_tvalid(axis_image_small_2_tvalid),
        .axis_resize_2_tready(axis_image_small_2_tready),
        .axis_resize_2_tlast(axis_image_small_2_tlast),

        .axis_resize_3_tdata(axis_image_small_3_tdata),
        .axis_resize_3_tkeep(axis_image_small_3_tkeep),
        .axis_resize_3_tuser(axis_image_small_3_tuser),
        .axis_resize_3_tvalid(axis_image_small_3_tvalid),
        .axis_resize_3_tready(axis_image_small_3_tready),
        .axis_resize_3_tlast(axis_image_small_3_tlast)
    );

    wire [TDATA_WIDTH - 1:0] axis_tensor_0_tdata;
    wire [TKEEP_WIDTH - 1:0] axis_tensor_0_tkeep;
    wire [TUSER_WIDTH - 1:0] axis_tensor_0_tuser;
    wire                     axis_tensor_0_tvalid;
    wire                     axis_tensor_0_tready;
    wire                     axis_tensor_0_tlast;

    wire [TDATA_WIDTH - 1:0] axis_tensor_1_tdata;
    wire [TKEEP_WIDTH - 1:0] axis_tensor_1_tkeep;
    wire [TUSER_WIDTH - 1:0] axis_tensor_1_tuser;
    wire                     axis_tensor_1_tvalid;
    wire                     axis_tensor_1_tready;
    wire                     axis_tensor_1_tlast;

    wire [TDATA_WIDTH - 1:0] axis_tensor_2_tdata;
    wire [TKEEP_WIDTH - 1:0] axis_tensor_2_tkeep;
    wire [TUSER_WIDTH - 1:0] axis_tensor_2_tuser;
    wire                     axis_tensor_2_tvalid;
    wire                     axis_tensor_2_tready;
    wire                     axis_tensor_2_tlast;

    wire [TDATA_WIDTH - 1:0] axis_tensor_3_tdata;
    wire [TKEEP_WIDTH - 1:0] axis_tensor_3_tkeep;
    wire [TUSER_WIDTH - 1:0] axis_tensor_3_tuser;
    wire                     axis_tensor_3_tvalid;
    wire                     axis_tensor_3_tready;
    wire                     axis_tensor_3_tlast;

    // Process
    small_axis_image_to_tensor_scaler tensor_scaler_0
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_image_tdata(axis_image_small_0_tdata),
        .axis_image_tkeep(axis_image_small_0_tkeep),
        .axis_image_tuser(axis_image_small_0_tuser),
        .axis_image_tvalid(axis_image_small_0_tvalid),
        .axis_image_tready(axis_image_small_0_tready),
        .axis_image_tlast(axis_image_small_0_tlast),

        .axis_tensor_tdata(axis_tensor_0_tdata),
        .axis_tensor_tkeep(axis_tensor_0_tkeep),
        .axis_tensor_tuser(axis_tensor_0_tuser),
        .axis_tensor_tvalid(axis_tensor_0_tvalid),
        .axis_tensor_tready(axis_tensor_0_tready),
        .axis_tensor_tlast(axis_tensor_0_tlast)
    );

    small_axis_image_to_tensor_scaler tensor_scaler_1
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_image_tdata(axis_image_small_1_tdata),
        .axis_image_tkeep(axis_image_small_1_tkeep),
        .axis_image_tuser(axis_image_small_1_tuser),
        .axis_image_tvalid(axis_image_small_1_tvalid),
        .axis_image_tready(axis_image_small_1_tready),
        .axis_image_tlast(axis_image_small_1_tlast),

        .axis_tensor_tdata(axis_tensor_1_tdata),
        .axis_tensor_tkeep(axis_tensor_1_tkeep),
        .axis_tensor_tuser(axis_tensor_1_tuser),
        .axis_tensor_tvalid(axis_tensor_1_tvalid),
        .axis_tensor_tready(axis_tensor_1_tready),
        .axis_tensor_tlast(axis_tensor_1_tlast)
    );

    small_axis_image_to_tensor_scaler tensor_scaler_2
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_image_tdata(axis_image_small_2_tdata),
        .axis_image_tkeep(axis_image_small_2_tkeep),
        .axis_image_tuser(axis_image_small_2_tuser),
        .axis_image_tvalid(axis_image_small_2_tvalid),
        .axis_image_tready(axis_image_small_2_tready),
        .axis_image_tlast(axis_image_small_2_tlast),

        .axis_tensor_tdata(axis_tensor_2_tdata),
        .axis_tensor_tkeep(axis_tensor_2_tkeep),
        .axis_tensor_tuser(axis_tensor_2_tuser),
        .axis_tensor_tvalid(axis_tensor_2_tvalid),
        .axis_tensor_tready(axis_tensor_2_tready),
        .axis_tensor_tlast(axis_tensor_2_tlast)
    );

    small_axis_image_to_tensor_scaler tensor_scaler_3
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_image_tdata(axis_image_small_3_tdata),
        .axis_image_tkeep(axis_image_small_3_tkeep),
        .axis_image_tuser(axis_image_small_3_tuser),
        .axis_image_tvalid(axis_image_small_3_tvalid),
        .axis_image_tready(axis_image_small_3_tready),
        .axis_image_tlast(axis_image_small_3_tlast),

        .axis_tensor_tdata(axis_tensor_3_tdata),
        .axis_tensor_tkeep(axis_tensor_3_tkeep),
        .axis_tensor_tuser(axis_tensor_3_tuser),
        .axis_tensor_tvalid(axis_tensor_3_tvalid),
        .axis_tensor_tready(axis_tensor_3_tready),
        .axis_tensor_tlast(axis_tensor_3_tlast)
    );

    // Combine
    axis_transmission_combiner transmission_combiner
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_input_0_tdata(axis_tensor_0_tdata),
        .axis_input_0_tkeep(axis_tensor_0_tkeep),
        .axis_input_0_tuser(axis_tensor_0_tuser),
        .axis_input_0_tvalid(axis_tensor_0_tvalid),
        .axis_input_0_tready(axis_tensor_0_tready),
        .axis_input_0_tlast(axis_tensor_0_tlast),

        .axis_input_1_tdata(axis_tensor_1_tdata),
        .axis_input_1_tkeep(axis_tensor_1_tkeep),
        .axis_input_1_tuser(axis_tensor_1_tuser),
        .axis_input_1_tvalid(axis_tensor_1_tvalid),
        .axis_input_1_tready(axis_tensor_1_tready),
        .axis_input_1_tlast(axis_tensor_1_tlast),

        .axis_input_2_tdata(axis_tensor_2_tdata),
        .axis_input_2_tkeep(axis_tensor_2_tkeep),
        .axis_input_2_tuser(axis_tensor_2_tuser),
        .axis_input_2_tvalid(axis_tensor_2_tvalid),
        .axis_input_2_tready(axis_tensor_2_tready),
        .axis_input_2_tlast(axis_tensor_2_tlast),

        .axis_input_3_tdata(axis_tensor_3_tdata),
        .axis_input_3_tkeep(axis_tensor_3_tkeep),
        .axis_input_3_tuser(axis_tensor_3_tuser),
        .axis_input_3_tvalid(axis_tensor_3_tvalid),
        .axis_input_3_tready(axis_tensor_3_tready),
        .axis_input_3_tlast(axis_tensor_3_tlast),

        .axis_combined_tdata(axis_tensor_tdata),
        .axis_combined_tkeep(axis_tensor_tkeep),
        .axis_combined_tuser(axis_tensor_tuser),
        .axis_combined_tvalid(axis_tensor_tvalid),
        .axis_combined_tready(axis_tensor_tready),
        .axis_combined_tlast(axis_tensor_tlast)
    );

endmodule