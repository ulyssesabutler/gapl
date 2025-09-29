module axis_flattener 
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
    input  [TDATA_WIDTH - 1:0] axis_original_tdata,
    input  [TDATA_WIDTH - 1:0] axis_original_tkeep,
    input  [TUSER_WIDTH - 1:0] axis_original_tuser,
    input                      axis_original_tvalid,
    output                     axis_original_tready,
    input                      axis_original_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] axis_flattened_tdata,
    output [TDATA_WIDTH - 1:0] axis_flattened_tkeep,
    output [TUSER_WIDTH - 1:0] axis_flattened_tuser,
    output                     axis_flattened_tvalid,
    input                      axis_flattened_tready,
    output                     axis_flattened_tlast
);

    wire read;
    wire write;

    wire can_read;
    wire can_write;

    fallthrough_variable_width_queue
    #(
        .IN_TDATA_WIDTH(TDATA_WIDTH),
        .OUT_TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    flattener
    (
        .clk(axis_aclk),
        .resetn(axis_resetn),

        .tdata_in(axis_original_tdata),
        .tkeep_in(axis_original_tkeep),
        .tuser_in(axis_original_tuser),
        .tlast_in(axis_original_tlast),
        .write(write),

        .tdata_out(axis_flattened_tdata),
        .tkeep_out(axis_flattened_tkeep),
        .tuser_out(axis_flattened_tuser),
        .tlast_out(axis_flattened_tlast),
        .read(read),

        .can_write(can_write),
        .can_read(can_read)
    );

    assign write                 = axis_original_tready & axis_original_tvalid;
    assign axis_original_tready  = can_write;

    assign read                  = axis_flattened_tready & axis_flattened_tvalid;
    assign axis_flattened_tvalid = can_read;

endmodule