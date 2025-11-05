module processor_controller
#(
    parameter  TDATA_WIDTH = 256,
    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
) (
    input  wire                     clock,
    input  wire                     reset,

    input  wire [TDATA_WIDTH - 1:0] ingress_in_tdata,
    input  wire [TKEEP_WIDTH - 1:0] ingress_in_tkeep,
    input  wire                     ingress_in_tvalid,
    output wire                     ingress_in_tready,
    input  wire                     ingress_in_tlast,

    output wire [TDATA_WIDTH - 1:0] ingress_out_tdata,
    output wire [TKEEP_WIDTH - 1:0] ingress_out_tkeep,
    output wire                     ingress_out_tlast,

    output wire                     enable,

    input  wire [TDATA_WIDTH - 1:0] egress_in_tdata,
    input  wire [TKEEP_WIDTH - 1:0] egress_in_tkeep,
    input  wire                     egress_in_tvalid,
    input  wire                     egress_in_tlast,

    output wire [TDATA_WIDTH - 1:0] egress_out_tdata,
    output wire [TKEEP_WIDTH - 1:0] egress_out_tkeep,
    output wire                     egress_out_tvalid,
    input  wire                     egress_out_tready,
    output wire                     egress_out_tlast
);

    // Convinient defintions
    wire reading_in              = ingress_in_tvalid & ingress_in_tready;
    wire writing_out             = egress_out_tvalid & egress_out_tready;

    // FSM
    localparam STATE_STARTING = 0;
    localparam STATE_RUNNING  = 1;
    localparam STATE_COMPLETE = 2;

    reg [2:0] state;
    reg [2:0] state_next;

    always @(*) begin
        state_next = state;

        case (state)

            STATE_STARTING: begin
                if (reading_in & ingress_in_tlast) state_next = STATE_RUNNING;
            end

            STATE_RUNNING: begin
                if (writing_out & egress_out_tlast) state_next = STATE_STARTING;
            end

        endcase
    end

    // Enabling
    reg enable;

    always @(*) begin
        enable = 0;

        case (state)

            STATE_STARTING: begin
                if (ingress_in_tvalid & egress_out_tready) enable = 1;
            end

            STATE_RUNNING: begin
                if (egress_out_tready) enable = 1;
            end

        endcase
    end

    // Hook-up ingress and egress
    assign ingress_out_tdata  = ingress_in_tdata;
    assign ingress_out_tkeep  = ingress_in_tkeep;
    assign ingress_in_tready  = enable; // Don't send if the module is disabled
    assign ingress_out_tlast  = ingress_in_tlast;

    assign egress_out_tdata   = egress_in_tdata;
    assign egress_out_tkeep   = egress_in_tkeep;
    assign egress_out_tvalid  = egress_in_tvalid & enable;
    assign egress_out_tlast   = egress_in_tlast;

    // Register
    always @(posedge clock) begin
        if (reset) begin
            state <= 0;
        end else begin
            state <= state_next;
        end
    end

endmodule
