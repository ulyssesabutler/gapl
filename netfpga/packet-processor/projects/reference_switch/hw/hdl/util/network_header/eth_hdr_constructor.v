/* ETHERNET HEADER CONSTRUCTOR
 * ===========================
 * Construct an ethernet header
 */
module eth_hdr_constructor
#(
    localparam MAC_ADDRESS_WIDTH = 48,
    localparam ETH_HDR_WIDTH     = 14 * 8
)
(
    input [MAC_ADDRESS_WIDTH - 1:0] src_mac_addr,
    input [MAC_ADDRESS_WIDTH - 1:0] dest_mac_addr,

    output [ETH_HDR_WIDTH - 1:0]    eth_hdr
);

    assign eth_hdr[95:48]  = src_mac_addr;
    assign eth_hdr[47:0]   = dest_mac_addr;
    assign eth_hdr[111:96] = 16'h0800;      // Type

endmodule