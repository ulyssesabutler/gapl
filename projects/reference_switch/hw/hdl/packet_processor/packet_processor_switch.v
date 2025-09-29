/* PACKET SWITCH
 * =============
 * This module determines whether a given packet is part of an inference request that should be
 * preprocessed before sending to the GPU, or if its another packet (such as an ARP packet) that
 * should be forwarded as normal.
 */
module packet_processor_switch
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
    input  [TDATA_WIDTH - 1:0] packet_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0] packet_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0] packet_in_axis_tuser,
    input                      packet_in_axis_tvalid,
    output                     packet_in_axis_tready,
    input                      packet_in_axis_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] packet_to_process_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0] packet_to_process_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0] packet_to_process_out_axis_tuser,
    output                     packet_to_process_out_axis_tvalid,
    input                      packet_to_process_out_axis_tready,
    output                     packet_to_process_out_axis_tlast,

    output [TDATA_WIDTH - 1:0] packet_to_bypass_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0] packet_to_bypass_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0] packet_to_bypass_out_axis_tuser,
    output                     packet_to_bypass_out_axis_tvalid,
    input                      packet_to_bypass_out_axis_tready,
    output                     packet_to_bypass_out_axis_tlast
);

    // TODO: For now, we're just fowarding all packets to the preprocessor
    assign packet_to_process_out_axis_tdata  = packet_in_axis_tdata;
    assign packet_to_process_out_axis_tkeep  = packet_in_axis_tkeep;
    assign packet_to_process_out_axis_tuser  = packet_in_axis_tuser;
    assign packet_to_process_out_axis_tvalid = packet_in_axis_tvalid;
    assign packet_in_axis_tready             = packet_to_process_out_axis_tready;
    assign packet_to_process_out_axis_tlast  = packet_in_axis_tlast;

    // This leaves the bypass unused
    assign packet_to_bypass_out_axis_tdata  = 0;
    assign packet_to_bypass_out_axis_tkeep  = 0;
    assign packet_to_bypass_out_axis_tuser  = 0;
    assign packet_to_bypass_out_axis_tvalid = 0;
    assign packet_to_bypass_out_axis_tlast  = 0;

endmodule