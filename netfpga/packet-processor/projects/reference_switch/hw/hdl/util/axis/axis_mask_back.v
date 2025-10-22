/* AXI STREAM TRIM FRONT
 * =====================
 * This module removes the first bytes of an AXI Stream, before forwarding the rest of the AXI Stream, unaltered. The
 * number of bytes removed is dictated by the BYTES_TRIMMED parameter.
 *
 * This module assumes the incoming data stream is flattened.
 */
module axis_mask_back
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    parameter BYTES_DROPPED           = 0,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8,
    localparam BYTES_PER_TRANSMISSION = TKEEP_WIDTH
)
(
    // Global ports
    input                      clock,
    input                      reset_n,

    // Module input
    input  [TDATA_WIDTH - 1:0] in_tdata,
    input  [TKEEP_WIDTH - 1:0] in_tkeep,
    input  [TUSER_WIDTH - 1:0] in_tuser,
    input                      in_tvalid,
    output                     in_tready,
    input                      in_tlast,

    // Module output
    output reg [TKEEP_WIDTH - 1:0] mask
);

    reg [31:0] bytes_read;
    reg [31:0] bytes_read_next;

    always @(*) begin
        bytes_read_next = bytes_read;

        if (in_tvalid & in_tready) begin
            if (in_tlast) begin
                bytes_read_next = 0;
            end else begin
                bytes_read_next = bytes_read + TKEEP_WIDTH;
            end
        end
    end

    always @(*) begin
        mask = -1;

        if (bytes_read + TKEEP_WIDTH <= BYTES_DROPPED) begin
            mask = 0;
        end else if (bytes_read <= BYTES_DROPPED) begin
            mask = ~(-1 >> ((bytes_read + TKEEP_WIDTH) - BYTES_DROPPED));
        end
    end

    always @(posedge clock) begin
        if (!reset_n) begin
            bytes_read <= 0;
        end else begin
            bytes_read <= bytes_read_next;
        end
    end

endmodule