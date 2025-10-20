/* INFERENCE REQUEST PREPROCESSOR
 * ==============================
 * The input to this module will be a stream of network packets that are parts of inference requests.
 * We want to take those packets and process them so the request can be sent directly to the GPU.
 * - Decoding: Converting the JPEG image to a bitmap
 * - Cropping: Removing the edges
 * - Resizing: Scaling the image to the correct size
 * - Tensor Conversion: Convert the bitmap into a neural network input, in the form of a tensor
 */
module packet_processor
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    localparam MAC_ADDRESS_WIDTH      = 48,
    localparam IP_ADDRESS_WIDTH       = 32,
    localparam IP_LENGTH_WIDTH        = 16,
    localparam IP_ID_WIDTH            = 16,
    localparam PORT_WIDTH             = 16,
    localparam UDP_LENGTH_WIDTH       = 16,

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
    wire [IP_ID_WIDTH - 1:0]            parsed_ip_id;
    wire [IP_LENGTH_WIDTH - 1:0]        parsed_ip_length;

    wire [PORT_WIDTH - 1:0]             parsed_src_port;
    wire [PORT_WIDTH - 1:0]             parsed_dest_port;
    wire [UDP_LENGTH_WIDTH - 1:0]       parsed_udp_length;

    // The body stream wires
    wire [TDATA_WIDTH - 1:0]            parsed_packet_body_axis_tdata;
    wire [TKEEP_WIDTH - 1:0]            parsed_packet_body_axis_tkeep;
    wire [TUSER_WIDTH - 1:0]            parsed_packet_body_axis_tuser;
    wire                                parsed_packet_body_axis_tvalid;
    wire                                parsed_packet_body_axis_tready;
    wire                                parsed_packet_body_axis_tlast;

    // Instantiate the parser
    packet_parser
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
        .ip_id_out(parsed_ip_id),
        .ip_length_out(parsed_ip_length),

        .src_port_out(parsed_src_port),
        .dest_port_out(parsed_dest_port),
        .udp_length_out(parsed_udp_length),

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
    wire [IP_ID_WIDTH - 1:0]           processed_ip_id;
    wire [IP_LENGTH_WIDTH - 1:0]       processed_ip_length;

    wire [PORT_WIDTH - 1:0]            processed_src_port;
    wire [PORT_WIDTH - 1:0]            processed_dest_port;
    wire [UDP_LENGTH_WIDTH - 1:0]      processed_udp_length;

    // Output body stream wires
    wire [TDATA_WIDTH - 1:0]           processed_packet_body_axis_tdata;
    wire [TKEEP_WIDTH - 1:0]           processed_packet_body_axis_tkeep;
    wire [TUSER_WIDTH - 1:0]           processed_packet_body_axis_tuser;
    wire                               processed_packet_body_axis_tvalid;
    wire                               processed_packet_body_axis_tready;
    wire                               processed_packet_body_axis_tlast;

    // Instantiate the module
    gapl_wrapper
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    processor
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .src_mac_addr_in(parsed_src_mac_addr),
        .dest_mac_addr_in(parsed_dest_mac_addr),

        .src_ip_addr_in(parsed_src_ip_addr),
        .dest_ip_addr_in(parsed_dest_ip_addr),
        .ip_id_in(parsed_ip_id),
        .ip_length_in(parsed_ip_length),

        .src_port_in(parsed_src_port),
        .dest_port_in(parsed_dest_port),
        .udp_length_in(parsed_udp_length),

        .packet_body_in_axis_tdata(parsed_packet_body_axis_tdata),
        .packet_body_in_axis_tkeep(parsed_packet_body_axis_tkeep),
        .packet_body_in_axis_tuser(parsed_packet_body_axis_tuser),
        .packet_body_in_axis_tvalid(parsed_packet_body_axis_tvalid),
        .packet_body_in_axis_tready(parsed_packet_body_axis_tready),
        .packet_body_in_axis_tlast(parsed_packet_body_axis_tlast),

        .src_mac_addr_out(processed_src_mac_addr),
        .dest_mac_addr_out(processed_dest_mac_addr),
        .ip_id_out(processed_ip_id),
        .ip_length_out(processed_ip_length),

        .src_ip_addr_out(processed_src_ip_addr),
        .dest_ip_addr_out(processed_dest_ip_addr),
        .udp_length_out(processed_udp_length),

        .src_port_out(processed_src_port),
        .dest_port_out(processed_dest_port),

        .packet_body_out_axis_tdata(processed_packet_body_axis_tdata),
        .packet_body_out_axis_tkeep(processed_packet_body_axis_tkeep),
        .packet_body_out_axis_tuser(processed_packet_body_axis_tuser),
        .packet_body_out_axis_tvalid(processed_packet_body_axis_tvalid),
        .packet_body_out_axis_tready(processed_packet_body_axis_tready),
        .packet_body_out_axis_tlast(processed_packet_body_axis_tlast)
    );


    // STAGE 3: Package the processed data
    wire [TDATA_WIDTH - 1:0]           packed_axis_tdata;
    wire [TKEEP_WIDTH - 1:0]           packed_axis_tkeep;
    wire [TUSER_WIDTH - 1:0]           packed_axis_tuser;
    wire                               packed_axis_tvalid;
    wire                               packed_axis_tready;
    wire                               packed_axis_tlast;

    packet_packer
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
        .ip_id_in(processed_ip_id),
        .ip_length_in(processed_ip_length),

        .src_port_in(processed_src_port),
        .dest_port_in(processed_dest_port),
        .udp_length_in(processed_udp_length),

        .packet_body_in_axis_tdata(processed_packet_body_axis_tdata),
        .packet_body_in_axis_tkeep(processed_packet_body_axis_tkeep),
        .packet_body_in_axis_tuser(processed_packet_body_axis_tuser),
        .packet_body_in_axis_tvalid(processed_packet_body_axis_tvalid),
        .packet_body_in_axis_tready(processed_packet_body_axis_tready),
        .packet_body_in_axis_tlast(processed_packet_body_axis_tlast),

        .packet_out_axis_tdata(packed_axis_tdata),
        .packet_out_axis_tkeep(packed_axis_tkeep),
        .packet_out_axis_tuser(packed_axis_tuser),
        .packet_out_axis_tvalid(packed_axis_tvalid),
        .packet_out_axis_tready(packed_axis_tready),
        .packet_out_axis_tlast(packed_axis_tlast)
    );

    // STAGE 4: Flattening
    axis_flattener
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    flattener
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_original_tdata(packed_axis_tdata),
        .axis_original_tkeep(packed_axis_tkeep),
        .axis_original_tuser(packed_axis_tuser),
        .axis_original_tvalid(packed_axis_tvalid),
        .axis_original_tready(packed_axis_tready),
        .axis_original_tlast(packed_axis_tlast),

        .axis_flattened_tdata(packet_out_axis_tdata),
        .axis_flattened_tkeep(packet_out_axis_tkeep),
        .axis_flattened_tuser(packet_out_axis_tuser),
        .axis_flattened_tvalid(packet_out_axis_tvalid),
        .axis_flattened_tready(packet_out_axis_tready),
        .axis_flattened_tlast(packet_out_axis_tlast)
    );

endmodule