module packet_body_processor
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    localparam MAC_ADDRESS_WIDTH      = 48,
    localparam IP_ADDRESS_WIDTH       = 32,
    localparam PORT_WIDTH             = 16,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8
) (
    // Global Ports
    input                                 axis_aclk,
    input                                 axis_resetn,

    // Module input
    input  [MAC_ADDRESS_WIDTH - 1:0]      src_mac_addr_in,
    input  [MAC_ADDRESS_WIDTH - 1:0]      dest_mac_addr_in,

    input  [IP_ADDRESS_WIDTH - 1:0]       src_ip_addr_in,
    input  [IP_ADDRESS_WIDTH - 1:0]       dest_ip_addr_in,

    input  [PORT_WIDTH - 1:0]             src_port_in,
    input  [PORT_WIDTH - 1:0]             dest_port_in,

    input  [TDATA_WIDTH - 1:0]            packet_body_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0]            packet_body_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0]            packet_body_in_axis_tuser,
    input                                 packet_body_in_axis_tvalid,
    output                                packet_body_in_axis_tready,
    input                                 packet_body_in_axis_tlast,

    // Module output
    output [MAC_ADDRESS_WIDTH - 1:0]     src_mac_addr_out,
    output [MAC_ADDRESS_WIDTH - 1:0]     dest_mac_addr_out,

    output [IP_ADDRESS_WIDTH - 1:0]      src_ip_addr_out,
    output [IP_ADDRESS_WIDTH - 1:0]      dest_ip_addr_out,

    output [PORT_WIDTH - 1:0]            src_port_out,
    output [PORT_WIDTH - 1:0]            dest_port_out,

    output [TDATA_WIDTH - 1:0]           packet_body_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0]           packet_body_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0]           packet_body_out_axis_tuser,
    output                               packet_body_out_axis_tvalid,
    input                                packet_body_out_axis_tready,
    output                               packet_body_out_axis_tlast
);

    /* TODO:
     * If we use a sequential module to process the body, we need to send the header data through a queue
     */

    assign src_mac_addr_out = src_mac_addr_in;
    assign dest_mac_addr_out = dest_mac_addr_in;

    assign src_ip_addr_out = src_ip_addr_in;
    assign dest_ip_addr_out = dest_ip_addr_in;

    assign src_port_out = src_port_in;
    assign dest_port_out = dest_port_in;

    assign packet_body_out_axis_tdata = packet_body_in_axis_tdata;
    assign packet_body_out_axis_tkeep = packet_body_in_axis_tkeep;
    assign packet_body_out_axis_tuser = packet_body_in_axis_tuser;
    assign packet_body_out_axis_tvalid = packet_body_in_axis_tvalid;
    assign packet_body_out_axis_tready = packet_body_in_axis_tready;
    assign packet_body_out_axis_tlast = packet_body_in_axis_tlast;

endmodule