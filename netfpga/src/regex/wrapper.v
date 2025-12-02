module gapl_wrapper
#(
    parameter TDATA_WIDTH  = 256,
    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
) (
    // Global Ports
    input  wire                     axis_aclk,
    input  wire                     axis_resetn,

    // Module input
    input  wire [TDATA_WIDTH - 1:0] packet_body_in_axis_tdata,
    input  wire [TKEEP_WIDTH - 1:0] packet_body_in_axis_tkeep,
    input  wire                     packet_body_in_axis_tvalid,
    output wire                     packet_body_in_axis_tready,
    input  wire                     packet_body_in_axis_tlast,

    // Module output
    output wire [TDATA_WIDTH - 1:0] packet_body_out_axis_tdata,
    output wire [TKEEP_WIDTH - 1:0] packet_body_out_axis_tkeep,
    output wire                     packet_body_out_axis_tvalid,
    input  wire                     packet_body_out_axis_tready,
    output wire                     packet_body_out_axis_tlast
);

    // Module I/O
    wire                     gapl_enable;
    wire                     gapl_reset;

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
    wire                     padder_in_tvalid;
    wire                     padder_in_tready;
    wire                     padder_in_tlast;

    wire [TDATA_WIDTH - 1:0] padder_out_tdata;
    wire [TKEEP_WIDTH - 1:0] padder_out_tkeep;
    wire                     padder_out_tvalid;
    wire                     padder_out_tready;
    wire                     padder_out_tlast;

    // MUX I/O
    wire [TDATA_WIDTH - 1:0] mux_in_tdata;
    wire [TKEEP_WIDTH - 1:0] mux_in_tkeep;
    wire                     mux_in_tvalid;
    wire                     mux_in_tready;
    wire                     mux_in_tlast;

    wire [TDATA_WIDTH - 1:0] mux_out_tdata;
    wire [TKEEP_WIDTH - 1:0] mux_out_tkeep;
    wire                     mux_out_tvalid;
    wire                     mux_out_tready;
    wire                     mux_out_tlast;

    axis_pad_output #( .TDATA_WIDTH(TDATA_WIDTH) ) padder
    (
        .clock(axis_aclk),
        .reset_n(axis_resetn),

        .ingress_in_tdata(packet_body_in_axis_tdata),
        .ingress_in_tkeep(packet_body_in_axis_tkeep),
        .ingress_in_tvalid(packet_body_in_axis_tvalid),
        .ingress_in_tready(packet_body_in_axis_tready),
        .ingress_in_tlast(packet_body_in_axis_tlast),

        .ingress_out_tdata(padder_in_tdata),
        .ingress_out_tkeep(padder_in_tkeep),
        .ingress_out_tvalid(padder_in_tvalid),
        .ingress_out_tready(padder_in_tready),
        .ingress_out_tlast(padder_in_tlast),

        .egress_in_tdata(padder_out_tdata),
        .egress_in_tkeep(padder_out_tkeep),
        .egress_in_tvalid(padder_out_tvalid),
        .egress_in_tready(padder_out_tready),
        .egress_in_tlast(padder_out_tlast),

        .egress_out_tdata(packet_body_out_axis_tdata),
        .egress_out_tkeep(packet_body_out_axis_tkeep),
        .egress_out_tvalid(packet_body_out_axis_tvalid),
        .egress_out_tready(packet_body_out_axis_tready),
        .egress_out_tlast(packet_body_out_axis_tlast)
    );

    axis_mutual_exclusion #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(1)
    ) mutual_exclusion (
        .clock(axis_aclk),
        .reset_n(axis_resetn),

        .ingress_in_tdata(padder_in_tdata),
        .ingress_in_tkeep(padder_in_tkeep),
        .ingress_in_tuser(0),
        .ingress_in_tvalid(padder_in_tvalid),
        .ingress_in_tready(padder_in_tready),
        .ingress_in_tlast(padder_in_tlast),

        .ingress_out_tdata(mux_in_tdata),
        .ingress_out_tkeep(mux_in_tkeep),
        .ingress_out_tuser(),
        .ingress_out_tvalid(mux_in_tvalid),
        .ingress_out_tready(mux_in_tready),
        .ingress_out_tlast(mux_in_tlast),

        .module_reset(gapl_reset),

        .egress_in_tdata(mux_out_tdata),
        .egress_in_tkeep(mux_out_tkeep),
        .egress_in_tuser(0),
        .egress_in_tvalid(mux_out_tvalid),
        .egress_in_tready(mux_out_tready),
        .egress_in_tlast(mux_out_tlast),

        .egress_out_tdata(padder_out_tdata),
        .egress_out_tkeep(padder_out_tkeep),
        .egress_out_tuser(),
        .egress_out_tvalid(padder_out_tvalid),
        .egress_out_tready(padder_out_tready),
        .egress_out_tlast(padder_out_tlast)
    );

    processor_controller #( .TDATA_WIDTH(TDATA_WIDTH) ) controller
    (
        .clock(axis_aclk),
        .reset(~axis_resetn),

        .ingress_in_tdata(mux_in_tdata),
        .ingress_in_tkeep(mux_in_tkeep),
        .ingress_in_tvalid(mux_in_tvalid),
        .ingress_in_tready(mux_in_tready),
        .ingress_in_tlast(mux_in_tlast),

        .ingress_out_tdata(gapl_in_tdata),
        .ingress_out_tkeep(gapl_in_tkeep),
        .ingress_out_tlast(gapl_in_tlast),

        .enable(gapl_enable),

        .egress_in_tdata(gapl_out_tdata),
        .egress_in_tkeep(gapl_out_tkeep),
        .egress_in_tvalid(gapl_out_tvalid),
        .egress_in_tlast(gapl_out_tlast),

        .egress_out_tdata(mux_out_tdata),
        .egress_out_tkeep(mux_out_tkeep),
        .egress_out_tvalid(mux_out_tvalid),
        .egress_out_tready(mux_out_tready),
        .egress_out_tlast(mux_out_tlast)
    );

    packet_body_processor gapl_processor
    (
        .clock(axis_aclk),
        .reset((!axis_resetn) || gapl_reset),
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