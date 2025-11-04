module gapl_wrapper
#(
    parameter TDATA_WIDTH  = 256,
    parameter TUSER_WIDTH  = 128,

    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
) (
    // Global Ports
    input  wire                     axis_aclk,
    input  wire                     axis_resetn,

    // Module input
    input  wire [TDATA_WIDTH - 1:0] packet_body_in_axis_tdata,
    input  wire [TKEEP_WIDTH - 1:0] packet_body_in_axis_tkeep,
    input  wire [TUSER_WIDTH - 1:0] packet_body_in_axis_tuser,
    input  wire                     packet_body_in_axis_tvalid,
    output wire                     packet_body_in_axis_tready,
    input  wire                     packet_body_in_axis_tlast,

    // Module output
    output wire [TDATA_WIDTH - 1:0] packet_body_out_axis_tdata,
    output wire [TKEEP_WIDTH - 1:0] packet_body_out_axis_tkeep,
    output wire [TUSER_WIDTH - 1:0] packet_body_out_axis_tuser,
    output wire                     packet_body_out_axis_tvalid,
    input  wire                     packet_body_out_axis_tready,
    output wire                     packet_body_out_axis_tlast
);

    // Module I/O
    wire                     gapl_enable;

    wire [TDATA_WIDTH - 1:0] gapl_in_tdata;
    wire [TKEEP_WIDTH - 1:0] gapl_in_tkeep;
    wire                     gapl_in_tlast;

    wire [TDATA_WIDTH - 1:0] gapl_out_tdata;
    wire [TKEEP_WIDTH - 1:0] gapl_out_tkeep;
    wire                     gapl_out_tvalid;
    wire                     gapl_out_tlast;

    // Padder I/O
    wire [TDATA_WIDTH - 1:0] padder_in_tdata;
    wire [TKEEP_WIDTH - 1:0] padder_in_tkeep;
    wire [TUSER_WIDTH - 1:0] padder_in_tuser;
    wire                     padder_in_tvalid;
    wire                     padder_in_tready;
    wire                     padder_in_tlast;

    wire [TDATA_WIDTH - 1:0] padder_out_tdata;
    wire [TKEEP_WIDTH - 1:0] padder_out_tkeep;
    wire [TUSER_WIDTH - 1:0] padder_out_tuser;
    wire                     padder_out_tvalid;
    wire                     padder_out_tready;
    wire                     padder_out_tlast;

    // User Tracker I/O
    wire [TDATA_WIDTH - 1:0] user_tracker_in_tdata;
    wire [TKEEP_WIDTH - 1:0] user_tracker_in_tkeep;
    wire                     user_tracker_in_tvalid;
    wire                     user_tracker_in_tready;
    wire                     user_tracker_in_tlast;

    wire [TDATA_WIDTH - 1:0] user_tracker_out_tdata;
    wire [TKEEP_WIDTH - 1:0] user_tracker_out_tkeep;
    wire                     user_tracker_out_tvalid;
    wire                     user_tracker_out_tready;
    wire                     user_tracker_out_tlast;

    axis_pad_output
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    padder
    (
        .clock(axis_aclk),
        .reset_n(axis_resetn),

        .ingress_in_tdata(packet_body_in_axis_tdata),
        .ingress_in_tkeep(packet_body_in_axis_tkeep),
        .ingress_in_tuser(packet_body_in_axis_tuser),
        .ingress_in_tvalid(packet_body_in_axis_tvalid),
        .ingress_in_tready(packet_body_in_axis_tready),
        .ingress_in_tlast(packet_body_in_axis_tlast),

        .ingress_out_tdata(padder_in_tdata),
        .ingress_out_tkeep(padder_in_tkeep),
        .ingress_out_tuser(padder_in_tuser),
        .ingress_out_tvalid(padder_in_tvalid),
        .ingress_out_tready(padder_in_tready),
        .ingress_out_tlast(padder_in_tlast),

        .egress_in_tdata(padder_out_tdata),
        .egress_in_tkeep(padder_out_tkeep),
        .egress_in_tuser(padder_out_tuser),
        .egress_in_tvalid(padder_out_tvalid),
        .egress_in_tready(padder_out_tready),
        .egress_in_tlast(padder_out_tlast),

        .egress_out_tdata(packet_body_out_axis_tdata),
        .egress_out_tkeep(packet_body_out_axis_tkeep),
        .egress_out_tuser(packet_body_out_axis_tuser),
        .egress_out_tvalid(packet_body_out_axis_tvalid),
        .egress_out_tready(packet_body_out_axis_tready),
        .egress_out_tlast(packet_body_out_axis_tlast)
    );

    axis_user_tracker
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    ) user_tracker (
        .clock(axis_aclk),
        .reset_n(axis_resetn),

        .ingress_in_tdata(padder_in_tdata),
        .ingress_in_tkeep(padder_in_tkeep),
        .ingress_in_tuser(padder_in_tuser),
        .ingress_in_tvalid(padder_in_tvalid),
        .ingress_in_tready(padder_in_tready),
        .ingress_in_tlast(padder_in_tlast),

        .ingress_out_tdata(user_tracker_in_tdata),
        .ingress_out_tkeep(user_tracker_in_tkeep),
        .ingress_out_tvalid(user_tracker_in_tvalid),
        .ingress_out_tready(user_tracker_in_tready),
        .ingress_out_tlast(user_tracker_in_tlast),

        .egress_in_tdata(user_tracker_out_tdata),
        .egress_in_tkeep(user_tracker_out_tkeep),
        .egress_in_tvalid(user_tracker_out_tvalid),
        .egress_in_tready(user_tracker_out_tready),
        .egress_in_tlast(user_tracker_out_tlast),

        .egress_out_tdata(padder_out_tdata),
        .egress_out_tkeep(padder_out_tkeep),
        .egress_out_tuser(padder_out_tuser),
        .egress_out_tvalid(padder_out_tvalid),
        .egress_out_tready(padder_out_tready),
        .egress_out_tlast(padder_out_tlast)
    );

    processor_controller
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    ) controller (
        .clock(axis_aclk),
        .reset(~axis_resetn),

        .ingress_in_tdata(user_tracker_in_tdata),
        .ingress_in_tkeep(user_tracker_in_tkeep),
        .ingress_in_tvalid(user_tracker_in_tvalid),
        .ingress_in_tready(user_tracker_in_tready),
        .ingress_in_tlast(user_tracker_in_tlast),

        .ingress_out_tdata(gapl_in_tdata),
        .ingress_out_tkeep(gapl_in_tkeep),
        .ingress_out_tlast(gapl_in_tlast),

        .enable(gapl_enable),

        .egress_in_tdata(gapl_out_tdata),
        .egress_in_tkeep(gapl_out_tkeep),
        .egress_in_tvalid(gapl_out_tvalid),
        .egress_in_tlast(gapl_out_tlast),

        .egress_out_tdata(user_tracker_out_tdata),
        .egress_out_tkeep(user_tracker_out_tkeep),
        .egress_out_tvalid(user_tracker_out_tvalid),
        .egress_out_tready(user_tracker_out_tready),
        .egress_out_tlast(user_tracker_out_tlast)
    );

    packet_body_processor gapl_processor
    (
        .clock(axis_aclk),
        .reset(!axis_resetn),
        .enable(gapl_enable),

        .i$data(gapl_in_tdata),
        .i$keep(gapl_in_tkeep),
        .i$last(gapl_in_tlast),

        .o$value$data(gapl_out_tdata),
        .o$value$keep(gapl_out_tkeep),
        .o$value$last(gapl_out_tlast),
        .o$valid(gapl_out_tvalid)
    );

endmodule