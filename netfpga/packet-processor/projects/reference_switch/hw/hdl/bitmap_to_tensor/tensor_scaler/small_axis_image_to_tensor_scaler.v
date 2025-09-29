module small_axis_image_to_tensor_scaler
#(
    parameter TDATA_WIDTH        = 256,
    parameter TUSER_WIDTH        = 128,

    localparam TKEEP_WIDTH       = TDATA_WIDTH / 8,
    
    localparam SMALL_TDATA_WIDTH = TDATA_WIDTH / 4,
    localparam SMALL_TKEEP_WIDTH = SMALL_TDATA_WIDTH / 8
)
(
    // Global Ports
    input                              axis_aclk,
    input                              axis_resetn,

    // Module input
    input  [SMALL_TDATA_WIDTH - 1:0]   axis_image_tdata,
    input  [SMALL_TKEEP_WIDTH - 1:0]   axis_image_tkeep,
    input  [TUSER_WIDTH - 1:0]         axis_image_tuser,
    input                              axis_image_tvalid,
    output                             axis_image_tready,
    input                              axis_image_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0]         axis_tensor_tdata,
    output [TKEEP_WIDTH - 1:0]         axis_tensor_tkeep,
    output [TUSER_WIDTH - 1:0]         axis_tensor_tuser,
    output                             axis_tensor_tvalid,
    input                              axis_tensor_tready,
    output                             axis_tensor_tlast
);

    genvar i;

    generate
        for (i = 0; i < SMALL_TDATA_WIDTH; i = i + 8) begin
            uint8_to_float32 scaling_lookup_table
            (
                .uint8(axis_image_tdata[i + 7:i]),
                .float32(axis_tensor_tdata[4*i + 31:4*i]),
                .axis_aclk(axis_aclk),
                .axis_resetn(axis_resetn)
            );
        end
    endgenerate
      
    genvar j;
    
    generate
        for (i = 0; i < SMALL_TKEEP_WIDTH; i = i + 1) begin
            assign axis_tensor_tkeep[4*i+3:4*i] = {4{axis_image_tkeep[i]}};
        end
    endgenerate

    assign axis_tensor_tuser  = axis_image_tuser;
    assign axis_tensor_tvalid = axis_image_tvalid;
    assign axis_image_tready  = axis_tensor_tready;
    assign axis_tensor_tlast  = axis_image_tlast;

endmodule