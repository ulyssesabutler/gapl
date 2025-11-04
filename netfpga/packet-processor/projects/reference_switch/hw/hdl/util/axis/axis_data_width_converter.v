module axis_data_width_converter
#(
    parameter IN_TDATA_WIDTH   = 256,
    parameter OUT_TDATA_WIDTH  = IN_TDATA_WIDTH / 4,
    parameter TUSER_WIDTH      = 128,

    localparam IN_TKEEP_WIDTH  = IN_TDATA_WIDTH / 8,
    localparam OUT_TKEEP_WIDTH = OUT_TDATA_WIDTH / 8
)
(
    // Global Ports
    input  wire                          axis_aclk,
    input  wire                          axis_resetn,

    // Module input
    input  wire  [IN_TDATA_WIDTH - 1:0]  axis_original_tdata,
    input  wire  [IN_TKEEP_WIDTH - 1:0]  axis_original_tkeep,
    input  wire  [TUSER_WIDTH - 1:0]     axis_original_tuser,
    input  wire                          axis_original_tvalid,
    output wire                          axis_original_tready,
    input  wire                          axis_original_tlast,

    // Module output
    output wire [OUT_TDATA_WIDTH - 1:0] axis_resize_tdata,
    output wire [OUT_TKEEP_WIDTH - 1:0] axis_resize_tkeep,
    output wire [TUSER_WIDTH - 1:0]     axis_resize_tuser,
    output wire                         axis_resize_tvalid,
    input  wire                         axis_resize_tready,
    output wire                         axis_resize_tlast
);

    fallthrough_variable_width_queue
    #(
        .IN_TDATA_WIDTH(IN_TDATA_WIDTH),
        .OUT_TDATA_WIDTH(OUT_TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    queue
    (
        .clk(axis_aclk),
        .resetn(axis_resetn),

        .tdata_in(axis_original_tdata),
        .tkeep_in(axis_original_tkeep),
        .tuser_in(axis_original_tuser),
        .tlast_in(axis_original_tlast),
        .write(axis_original_tvalid),

        .tdata_out(axis_resize_tdata),
        .tkeep_out(axis_resize_tkeep),
        .tuser_out(axis_resize_tuser),
        .tlast_out(axis_resize_tlast),
        .read(axis_resize_tready),

        .can_write(axis_original_tready),
        .can_read(axis_resize_tvalid)
    );

endmodule
