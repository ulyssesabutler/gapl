module gapl_wrapper
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8
) (
    // Global Ports
    input                                 axis_aclk,
    input                                 axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0]            packet_body_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0]            packet_body_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0]            packet_body_in_axis_tuser,
    input                                 packet_body_in_axis_tvalid,
    output                                packet_body_in_axis_tready,
    input                                 packet_body_in_axis_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0]            packet_body_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0]            packet_body_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0]            packet_body_out_axis_tuser,
    output                                packet_body_out_axis_tvalid,
    input                                 packet_body_out_axis_tready,
    output                                packet_body_out_axis_tlast
);

    // Module input
    wire [TDATA_WIDTH - 1:0] gapl_in_tdata;
    wire [TKEEP_WIDTH - 1:0] gapl_in_tkeep;
    wire [TUSER_WIDTH - 1:0] gapl_in_tuser;
    wire                     gapl_in_tvalid;
    wire                     gapl_in_tready;
    wire                     gapl_in_tlast;

    // Module output
    wire [TDATA_WIDTH - 1:0] gapl_out_tdata;
    wire [TKEEP_WIDTH - 1:0] gapl_out_tkeep;
    wire [TUSER_WIDTH - 1:0] gapl_out_tuser;
    wire                     gapl_out_tvalid;
    wire                     gapl_out_tready;
    wire                     gapl_out_tlast;

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

        .ingress_out_tdata(gapl_in_tdata),
        .ingress_out_tkeep(gapl_in_tkeep),
        .ingress_out_tuser(gapl_in_tuser),
        .ingress_out_tvalid(gapl_in_tvalid),
        .ingress_out_tready(gapl_in_tready),
        .ingress_out_tlast(gapl_in_tlast),

        .egress_in_tdata(gapl_out_tdata),
        .egress_in_tkeep(gapl_out_tkeep),
        .egress_in_tuser(gapl_out_tuser),
        .egress_in_tvalid(gapl_out_tvalid),
        .egress_in_tready(gapl_out_tready),
        .egress_in_tlast(gapl_out_tlast),

        .egress_out_tdata(packet_body_out_axis_tdata),
        .egress_out_tkeep(packet_body_out_axis_tkeep),
        .egress_out_tuser(packet_body_out_axis_tuser),
        .egress_out_tvalid(packet_body_out_axis_tvalid),
        .egress_out_tready(packet_body_out_axis_tready),
        .egress_out_tlast(packet_body_out_axis_tlast)
    );

    assign gapl_out_tvalid = gapl_in_tvalid;
    assign gapl_in_tready = gapl_out_tready;

    packet_body_processor gapl_processor
    (
        .clock(axis_aclk),
        .reset(!axis_resetn),
        .enable(1),

        .i$data(gapl_in_tdata),
        .i$keep(gapl_in_tkeep),
        .i$user(gapl_in_tuser),
        .i$last(gapl_in_tlast),

        .o$data(gapl_out_tdata),
        .o$keep(gapl_out_tkeep),
        .o$user(gapl_out_tuser),
        .o$last(gapl_out_tlast)
    );

endmodule