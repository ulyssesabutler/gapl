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

    assign packet_body_out_axis_tvalid = packet_body_in_axis_tvalid;
    assign packet_body_in_axis_tready = packet_body_out_axis_tready;

    packet_body_processor gapl_processor
    (
        .clock(axis_aclk),
        .reset(!axis_resetn),
        .enable(1),

        .i$data(packet_body_in_axis_tdata),
        .i$keep(packet_body_in_axis_tkeep),
        .i$user(packet_body_in_axis_tuser),
        .i$last(packet_body_in_axis_tlast),

        .o$data(packet_body_out_axis_tdata),
        .o$keep(packet_body_out_axis_tkeep),
        .o$user(packet_body_out_axis_tuser),
        .o$last(packet_body_out_axis_tlast)
    );

endmodule