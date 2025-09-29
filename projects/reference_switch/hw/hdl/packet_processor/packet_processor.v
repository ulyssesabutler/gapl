/* PACKET PROCESSOR
 * ================
 * This module is designed to be a stage in the nf_datapath pipeline. It represent the pipeline that will
 * process each packet, either sending it throught the preprocessor if it's an inference request, or
 * forwarding the raw packet, otherwise. It takes a stream of packets as an input, and returns a stream of
 * packets as its output.
 */
module packet_processor
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
    output [TDATA_WIDTH - 1:0] packet_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0] packet_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0] packet_out_axis_tuser,
    output                     packet_out_axis_tvalid,
    input                      packet_out_axis_tready,
    output                     packet_out_axis_tlast
);


    // STAGE 1: PACKET SWITCH: Determines whether each packet will be sent through the preprocessor, or bypass

    // First, create the wires. One set for packets destined for the preprocessor, and another set of wires to bypass it.
    wire [TDATA_WIDTH - 1:0] packet_to_process_axis_tdata;
    wire [TKEEP_WIDTH - 1:0] packet_to_process_axis_tkeep;
    wire [TUSER_WIDTH - 1:0] packet_to_process_axis_tuser;
    wire                     packet_to_process_axis_tvalid;
    wire                     packet_to_process_axis_tready;
    wire                     packet_to_process_axis_tlast;

    wire [TDATA_WIDTH - 1:0] packet_to_bypass_axis_tdata;
    wire [TKEEP_WIDTH - 1:0] packet_to_bypass_axis_tkeep;
    wire [TUSER_WIDTH - 1:0] packet_to_bypass_axis_tuser;
    wire                     packet_to_bypass_axis_tvalid;
    wire                     packet_to_bypass_axis_tready;
    wire                     packet_to_bypass_axis_tlast;

    // Second, instantiate the switch module
    packet_processor_switch
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    switch
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .packet_in_axis_tdata(packet_in_axis_tdata),
        .packet_in_axis_tkeep(packet_in_axis_tkeep),
        .packet_in_axis_tuser(packet_in_axis_tuser),
        .packet_in_axis_tvalid(packet_in_axis_tvalid),
        .packet_in_axis_tready(packet_in_axis_tready),
        .packet_in_axis_tlast(packet_in_axis_tlast),

        .packet_to_process_out_axis_tdata(packet_to_process_axis_tdata),
        .packet_to_process_out_axis_tkeep(packet_to_process_axis_tkeep),
        .packet_to_process_out_axis_tuser(packet_to_process_axis_tuser),
        .packet_to_process_out_axis_tvalid(packet_to_process_axis_tvalid),
        .packet_to_process_out_axis_tready(packet_to_process_axis_tready),
        .packet_to_process_out_axis_tlast(packet_to_process_axis_tlast),

        .packet_to_bypass_out_axis_tdata(packet_to_bypass_axis_tdata),
        .packet_to_bypass_out_axis_tkeep(packet_to_bypass_axis_tkeep),
        .packet_to_bypass_out_axis_tuser(packet_to_bypass_axis_tuser),
        .packet_to_bypass_out_axis_tvalid(packet_to_bypass_axis_tvalid),
        .packet_to_bypass_out_axis_tready(packet_to_bypass_axis_tready),
        .packet_to_bypass_out_axis_tlast(packet_to_bypass_axis_tlast)
    );


    // STAGE 2: PACKET PREPROCESSOR: Once we've determined that a packet is an inference request, we can start processing the image to make it suitable as a neural network input

    // First, create the output wires
    wire [TDATA_WIDTH - 1:0] processed_packet_axis_tdata;
    wire [TKEEP_WIDTH - 1:0] processed_packet_axis_tkeep;
    wire [TUSER_WIDTH - 1:0] processed_packet_axis_tuser;
    wire                     processed_packet_axis_tvalid;
    wire                     processed_packet_axis_tready;
    wire                     processed_packet_axis_tlast;

    // Second, instantiate the preprocessor
    inference_request_preprocessor
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    preprocessor
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .packet_in_axis_tdata(packet_to_process_axis_tdata),
        .packet_in_axis_tkeep(packet_to_process_axis_tkeep),
        .packet_in_axis_tuser(packet_to_process_axis_tuser),
        .packet_in_axis_tvalid(packet_to_process_axis_tvalid),
        .packet_in_axis_tready(packet_to_process_axis_tready),
        .packet_in_axis_tlast(packet_to_process_axis_tlast),

        .packet_out_axis_tdata(processed_packet_axis_tdata),
        .packet_out_axis_tkeep(processed_packet_axis_tkeep),
        .packet_out_axis_tuser(processed_packet_axis_tuser),
        .packet_out_axis_tvalid(processed_packet_axis_tvalid),
        .packet_out_axis_tready(processed_packet_axis_tready),
        .packet_out_axis_tlast(processed_packet_axis_tlast)
    );


    // STAGE 3: PACKET TRANSMITTER: Sends packets out of this module from the preprocessor and its bypass

    // Instantiate the transmitter
    packet_processor_transmitter
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    transmitter
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .processed_packet_in_axis_tdata(processed_packet_axis_tdata),
        .processed_packet_in_axis_tkeep(processed_packet_axis_tkeep),
        .processed_packet_in_axis_tuser(processed_packet_axis_tuser),
        .processed_packet_in_axis_tvalid(processed_packet_axis_tvalid),
        .processed_packet_in_axis_tready(processed_packet_axis_tready),
        .processed_packet_in_axis_tlast(processed_packet_axis_tlast),

        .packet_from_bypass_in_axis_tdata(packet_to_bypass_axis_tdata),
        .packet_from_bypass_in_axis_tkeep(packet_to_bypass_axis_tkeep),
        .packet_from_bypass_in_axis_tuser(packet_to_bypass_axis_tuser),
        .packet_from_bypass_in_axis_tvalid(packet_to_bypass_axis_tvalid),
        .packet_from_bypass_in_axis_tready(packet_to_bypass_axis_tready),
        .packet_from_bypass_in_axis_tlast(packet_to_bypass_axis_tlast),

        .packet_out_axis_tdata(packet_out_axis_tdata),
        .packet_out_axis_tkeep(packet_out_axis_tkeep),
        .packet_out_axis_tuser(packet_out_axis_tuser),
        .packet_out_axis_tvalid(packet_out_axis_tvalid),
        .packet_out_axis_tready(packet_out_axis_tready),
        .packet_out_axis_tlast(packet_out_axis_tlast)
    );

endmodule