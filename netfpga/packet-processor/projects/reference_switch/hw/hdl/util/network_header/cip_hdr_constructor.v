/* CIP HEADER CONSTRUCTOR
 * ======================
 * Construct an image header
 */
module cip_hdr_constructor
#(
    localparam TRANSMISSION_ID_WIDTH = 32,
    localparam SEQUENCE_NUM_WIDTH    = 16,
    localparam CIP_HDR_WIDTH         = 8 * 8
)
(
    input                                last_flag,
    input                                error_flag,
    input  [TRANSMISSION_ID_WIDTH - 1:0] transmission_id,
    input  [SEQUENCE_NUM_WIDTH - 1:0]    sequence_num,

    output [CIP_HDR_WIDTH - 1:0]         cip_hdr
);

    assign cip_hdr[7]     = last_flag;
    assign cip_hdr[6]     = error_flag;
    assign cip_hdr[5:0]   = 6'h0; // Reserved
    assign cip_hdr[39:8]  = transmission_id;
    assign cip_hdr[55:40] = sequence_num;
    assign cip_hdr[63:56] = 8'h0; // Retry number

endmodule