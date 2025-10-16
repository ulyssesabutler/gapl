module axis_queue
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
    input  [TDATA_WIDTH - 1:0] in_tdata,
    input  [TKEEP_WIDTH - 1:0] in_tkeep,
    input  [TUSER_WIDTH - 1:0] in_tuser,
    input                      in_tvalid,
    output                     in_tready,
    input                      in_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] out_tdata,
    output [TKEEP_WIDTH - 1:0] out_tkeep,
    output [TUSER_WIDTH - 1:0] out_tuser,
    output                     out_tvalid,
    input                      out_tready,
    output                     out_tlast
);

    wire queue_nearly_full;
    wire queue_empty;

    wire queue_write;
    wire queue_read;

    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH + TUSER_WIDTH + TKEEP_WIDTH + 1),
        .MAX_DEPTH_BITS(2)
    )
    packer_input_queue
    (
        .din({
            in_tdata,
            in_tkeep,
            in_tuser,
            in_tlast
        }),
        .wr_en(queue_write),
        .rd_en(queue_read),
        .dout({
            out_tdata,
            out_tkeep,
            out_tuser,
            out_tlast
        }),
        .full        (),
        .prog_full   (),
        .nearly_full (queue_nearly_full),
        .empty       (queue_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    assign in_tready = ~queue_nearly_full;
    assign queue_write = in_tready & in_tvalid;

    assign out_tvalid = ~queue_empty;
    assign queue_read = out_tready & out_tvalid;

endmodule
