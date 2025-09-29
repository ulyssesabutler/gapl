/* IP HEADER CONSTRUCTOR
 * =====================
 * Construct an IP header
 */
module ip_hdr_constructor
#(
    localparam IP_ADDRESS_WIDTH = 32,
    localparam IP_HDR_WIDTH     = 20 * 8,
    localparam LENGTH_WIDTH     = 16,
    localparam ID_WIDTH         = 16
)
(
    input [IP_ADDRESS_WIDTH - 1:0] src_ip_addr,
    input [IP_ADDRESS_WIDTH - 1:0] dest_ip_addr,

    input [LENGTH_WIDTH - 1:0]     body_length,
    input [ID_WIDTH - 1:0]         id,

    output [IP_HDR_WIDTH - 1:0]    ip_hdr
);

    assign ip_hdr[7:0]     = 8'h45; // Version
    assign ip_hdr[15:8]    = 8'h0;  // DSCP
    assign ip_hdr[31:16]   = body_length + 20;
    assign ip_hdr[47:32]   = id;
    assign ip_hdr[63:48]   = 16'h0; // Fragmentation offset
    assign ip_hdr[71:64]   = 8'h40; // TTL
    assign ip_hdr[79:72]   = 8'h11; // Protocol (UDP)
    // Compute the header checksum below
    assign ip_hdr[127:96]  = src_ip_addr;
    assign ip_hdr[159:128] = dest_ip_addr;

    // Header checksum
    wire [16 * 9 - 1:0] checksum_operands = {ip_hdr[159:96], ip_hdr[79:0]};
    wire [15:0]         sum;

    ones_complement_sum #( .WIDTH(16), .OPERAND_COUNT(9) ) checksum_sum
    (
        .operands(checksum_operands),
        .sum(sum)
    );

    assign ip_hdr[95:80] = ~sum;

endmodule