/* UDP HEADER CONSTRUCTOR
 * ======================
 * Construct a UDP header
 */
 module udp_hdr_constructor
 #(
    localparam PORT_WIDTH    = 16,
    localparam LENGTH_WIDTH  = 16,
    localparam UDP_HDR_WIDTH = 8 * 8
 )
 (
    input  [PORT_WIDTH - 1:0]    src_port,
    input  [PORT_WIDTH - 1:0]    dest_port,
    input  [LENGTH_WIDTH - 1:0]  body_length,

    output [UDP_HDR_WIDTH - 1:0] udp_hdr
 );

    assign udp_hdr[15:0]  = src_port;
    assign udp_hdr[31:16] = dest_port;
    assign udp_hdr[47:32] = body_length + 20;
    assign udp_hdr[63:48] = 16'h0; // Checksum, optional, disabled for now

 endmodule