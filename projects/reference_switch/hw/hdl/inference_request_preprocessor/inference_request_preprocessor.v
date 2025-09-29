/* INFERENCE REQUEST PREPROCESSOR
 * ==============================
 * The input to this module will be a stream of network packets that are parts of inference requests.
 * We want to take those packets and process them so the request can be sent directly to the GPU.
 * - Decoding: Converting the JPEG image to a bitmap
 * - Cropping: Removing the edges
 * - Resizing: Scaling the image to the correct size
 * - Tensor Conversion: Convert the bitmap into a neural network input, in the form of a tensor
 */
module inference_request_preprocessor
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    localparam MAC_ADDRESS_WIDTH      = 48,
    localparam IP_ADDRESS_WIDTH       = 32,
    localparam PORT_WIDTH             = 16,
    localparam TRANSMISSION_ID_WIDTH  = 32,
    localparam CIP_SEQUENCE_NUM_WIDTH = 16,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8
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


    // STAGE 1: PACKET PARSER: Parse the headers of the packet to retrieve the packet metadata

    // The metadata wires
    wire [MAC_ADDRESS_WIDTH - 1:0]      parsed_src_mac_addr;
    wire [MAC_ADDRESS_WIDTH - 1:0]      parsed_dest_mac_addr;

    wire [IP_ADDRESS_WIDTH - 1:0]       parsed_src_ip_addr;
    wire [IP_ADDRESS_WIDTH - 1:0]       parsed_dest_ip_addr;

    wire [PORT_WIDTH - 1:0]             parsed_src_port;
    wire [PORT_WIDTH - 1:0]             parsed_dest_port;

    wire [TRANSMISSION_ID_WIDTH - 1:0]  parsed_transmission_id;

    wire [CIP_SEQUENCE_NUM_WIDTH - 1:0] parsed_sequence_number;
    wire                                parsed_last_packet;

    // The body stream wires
    wire [TDATA_WIDTH - 1:0]            parsed_packet_body_axis_tdata;
    wire [TKEEP_WIDTH - 1:0]            parsed_packet_body_axis_tkeep;
    wire [TUSER_WIDTH - 1:0]            parsed_packet_body_axis_tuser;
    wire                                parsed_packet_body_axis_tvalid;
    wire                                parsed_packet_body_axis_tready;
    wire                                parsed_packet_body_axis_tlast;

    // Instantiate the parser
    inference_request_packet_parser
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    parser
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .packet_in_axis_tdata(packet_in_axis_tdata),
        .packet_in_axis_tkeep(packet_in_axis_tkeep),
        .packet_in_axis_tuser(packet_in_axis_tuser),
        .packet_in_axis_tvalid(packet_in_axis_tvalid),
        .packet_in_axis_tready(packet_in_axis_tready),
        .packet_in_axis_tlast(packet_in_axis_tlast),

        .src_mac_addr_out(parsed_src_mac_addr),
        .dest_mac_addr_out(parsed_dest_mac_addr),

        .src_ip_addr_out(parsed_src_ip_addr),
        .dest_ip_addr_out(parsed_dest_ip_addr),

        .src_port_out(parsed_src_port),
        .dest_port_out(parsed_dest_port),

        .transmission_id_out(parsed_transmission_id),

        .sequence_number_out(parsed_sequence_number),
        .last_packet_out(parsed_last_packet),

        .packet_body_out_axis_tdata(parsed_packet_body_axis_tdata),
        .packet_body_out_axis_tkeep(parsed_packet_body_axis_tkeep),
        .packet_body_out_axis_tuser(parsed_packet_body_axis_tuser),
        .packet_body_out_axis_tvalid(parsed_packet_body_axis_tvalid),
        .packet_body_out_axis_tready(parsed_packet_body_axis_tready),
        .packet_body_out_axis_tlast(parsed_packet_body_axis_tlast)
    );


    // STAGE 2: DATA PREPROCESSOR

    // Output metadata wires
    wire [MAC_ADDRESS_WIDTH - 1:0]     processed_src_mac_addr;
    wire [MAC_ADDRESS_WIDTH - 1:0]     processed_dest_mac_addr;

    wire [IP_ADDRESS_WIDTH - 1:0]      processed_src_ip_addr;
    wire [IP_ADDRESS_WIDTH - 1:0]      processed_dest_ip_addr;

    wire [PORT_WIDTH - 1:0]            processed_src_port;
    wire [PORT_WIDTH - 1:0]            processed_dest_port;

    wire [TRANSMISSION_ID_WIDTH - 1:0] processed_transmission_id;

    // Output body stream wires
    wire [TDATA_WIDTH - 1:0]           processed_packet_body_axis_tdata;
    wire [TKEEP_WIDTH - 1:0]           processed_packet_body_axis_tkeep;
    wire [TUSER_WIDTH - 1:0]           processed_packet_body_axis_tuser;
    wire                               processed_packet_body_axis_tvalid;
    wire                               processed_packet_body_axis_tready;
    wire                               processed_packet_body_axis_tlast;

    // Instantiate the module
    inference_request_data_preprocessor
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    preprocessor
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .src_mac_addr_in(parsed_src_mac_addr),
        .dest_mac_addr_in(parsed_dest_mac_addr),

        .src_ip_addr_in(parsed_src_ip_addr),
        .dest_ip_addr_in(parsed_dest_ip_addr),

        .src_port_in(parsed_src_port),
        .dest_port_in(parsed_dest_port),

        .transmission_id_in(parsed_transmission_id),

        .sequence_number_in(parsed_sequence_number),
        .last_packet_in(parsed_last_packet),

        .packet_body_in_axis_tdata(parsed_packet_body_axis_tdata),
        .packet_body_in_axis_tkeep(parsed_packet_body_axis_tkeep),
        .packet_body_in_axis_tuser(parsed_packet_body_axis_tuser),
        .packet_body_in_axis_tvalid(parsed_packet_body_axis_tvalid),
        .packet_body_in_axis_tready(parsed_packet_body_axis_tready),
        .packet_body_in_axis_tlast(parsed_packet_body_axis_tlast),

        .src_mac_addr_out(processed_src_mac_addr),
        .dest_mac_addr_out(processed_dest_mac_addr),

        .src_ip_addr_out(processed_src_ip_addr),
        .dest_ip_addr_out(processed_dest_ip_addr),

        .src_port_out(processed_src_port),
        .dest_port_out(processed_dest_port),

        .transmission_id_out(processed_transmission_id),

        .packet_body_out_axis_tdata(processed_packet_body_axis_tdata),
        .packet_body_out_axis_tkeep(processed_packet_body_axis_tkeep),
        .packet_body_out_axis_tuser(processed_packet_body_axis_tuser),
        .packet_body_out_axis_tvalid(processed_packet_body_axis_tvalid),
        .packet_body_out_axis_tready(processed_packet_body_axis_tready),
        .packet_body_out_axis_tlast(processed_packet_body_axis_tlast)
    );


    // STAGE 3: Package the processed data
    inference_request_data_packer
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    packer
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .src_mac_addr_in(processed_src_mac_addr),
        .dest_mac_addr_in(processed_dest_mac_addr),

        .src_ip_addr_in(processed_src_ip_addr),
        .dest_ip_addr_in(processed_dest_ip_addr),

        .src_port_in(processed_src_port),
        .dest_port_in(processed_dest_port),

        .transmission_id_in(processed_transmission_id),

        .packet_body_in_axis_tdata(processed_packet_body_axis_tdata),
        .packet_body_in_axis_tkeep(processed_packet_body_axis_tkeep),
        .packet_body_in_axis_tuser(processed_packet_body_axis_tuser),
        .packet_body_in_axis_tvalid(processed_packet_body_axis_tvalid),
        .packet_body_in_axis_tready(processed_packet_body_axis_tready),
        .packet_body_in_axis_tlast(processed_packet_body_axis_tlast),

        .packet_out_axis_tdata(packet_out_axis_tdata),
        .packet_out_axis_tkeep(packet_out_axis_tkeep),
        .packet_out_axis_tuser(packet_out_axis_tuser),
        .packet_out_axis_tvalid(packet_out_axis_tvalid),
        .packet_out_axis_tready(packet_out_axis_tready),
        .packet_out_axis_tlast(packet_out_axis_tlast)
    );

endmodule