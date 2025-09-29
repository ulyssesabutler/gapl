/* PACKET TRANSMITTER
 * ==================
 * This module takes the wires from the output of the preprocessor, and the bypass wires, and forwards
 * the packets from each. This consolidates those two wires into a single stream of packets to send back
 * along the nf_datapath.
 */
module packet_processor_transmitter
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
    input  [TDATA_WIDTH - 1:0] processed_packet_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0] processed_packet_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0] processed_packet_in_axis_tuser,
    input                      processed_packet_in_axis_tvalid,
    output                     processed_packet_in_axis_tready,
    input                      processed_packet_in_axis_tlast,

    input  [TDATA_WIDTH - 1:0] packet_from_bypass_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0] packet_from_bypass_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0] packet_from_bypass_in_axis_tuser,
    input                      packet_from_bypass_in_axis_tvalid,
    output                     packet_from_bypass_in_axis_tready,
    input                      packet_from_bypass_in_axis_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] packet_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0] packet_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0] packet_out_axis_tuser,
    output                     packet_out_axis_tvalid,
    input                      packet_out_axis_tready,
    output                     packet_out_axis_tlast
);

    // TODO: For now, only the preprocessor line is being used. The bypass still needs to be added in the switch.
    assign packet_out_axis_tdata           = processed_packet_in_axis_tdata;
    assign packet_out_axis_tkeep           = processed_packet_in_axis_tkeep;
    assign packet_out_axis_tuser           = processed_packet_in_axis_tuser;
    assign packet_out_axis_tvalid          = processed_packet_in_axis_tvalid;
    assign processed_packet_in_axis_tready = packet_out_axis_tready;
    assign packet_out_axis_tlast           = processed_packet_in_axis_tlast;

    // This leaves the bypass unused
    assign packet_from_bypass_in_axis_tready = 0;

endmodule