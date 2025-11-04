module axis_hold_metadata
#(
    parameter TDATA_WIDTH  = 256,
    parameter TUSER_WIDTH  = 128,
    parameter METADATA_WIDTH = 128,

    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
)
(
    // Global Ports
    input  wire                         axis_aclk,
    input  wire                         axis_resetn,

    // Module input
    input  wire  [TDATA_WIDTH - 1:0]    in_tdata,
    input  wire  [TKEEP_WIDTH - 1:0]    in_tkeep,
    input  wire  [TUSER_WIDTH - 1:0]    in_tuser,
    input  wire  [METADATA_WIDTH - 1:0] in_metadata,
    input  wire                         in_tvalid,
    output wire                         in_tready,
    input  wire                         in_tlast,

    // Module output
    output wire [TDATA_WIDTH - 1:0]    out_tdata,
    output wire [TKEEP_WIDTH - 1:0]    out_tkeep,
    output wire [TUSER_WIDTH - 1:0]    out_tuser,
    output wire [METADATA_WIDTH - 1:0] out_metadata,
    output wire                        out_tvalid,
    input  wire                        out_tready,
    output wire                        out_tlast
);

    localparam STATE_WAITING      = 0;
    localparam STATE_TRANSMITTING = 1;

    reg [METADATA_WIDTH - 1:0] metadata;
    reg [METADATA_WIDTH - 1:0] metadata_next;

    assign out_metadata = metadata;

    reg state;
    reg state_next;

    axis_queue
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    queue
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .in_tdata(in_tdata),
        .in_tkeep(in_tkeep),
        .in_tuser(in_tuser),
        .in_tvalid(in_tvalid),
        .in_tready(in_tready),
        .in_tlast(in_tlast),

        .out_tdata(out_tdata),
        .out_tkeep(out_tkeep),
        .out_tuser(out_tuser),
        .out_tvalid(out_tvalid),
        .out_tready(out_tready),
        .out_tlast(out_tlast)
    );

    always @(*) begin
        metadata_next = metadata;
        state_next = state;

        case (state)

            STATE_WAITING: begin
                if (in_tvalid & in_tready) begin
                    metadata_next = in_metadata;
                    state = STATE_TRANSMITTING;
                end
            end

            STATE_TRANSMITTING: begin
                if (out_tvalid & out_tready & out_tlast) begin
                    state = STATE_WAITING;
                end
            end

        endcase
    end

    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            metadata <= 0;
            state <= 0;
        end else begin
            metadata <= metadata_next;
            state <= state_next;
        end
    end

endmodule