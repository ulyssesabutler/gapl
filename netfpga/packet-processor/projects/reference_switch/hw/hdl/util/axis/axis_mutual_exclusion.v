module axis_mutual_exclusion
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8
)
(
    // Global Ports
    input  wire                     clock,
    input  wire                     reset_n,

    // Module input
    input  wire [TDATA_WIDTH - 1:0] ingress_in_tdata,
    input  wire [TKEEP_WIDTH - 1:0] ingress_in_tkeep,
    input  wire [TUSER_WIDTH - 1:0] ingress_in_tuser,
    input  wire                     ingress_in_tvalid,
    output reg                      ingress_in_tready,
    input  wire                     ingress_in_tlast,

    output reg  [TDATA_WIDTH - 1:0] ingress_out_tdata,
    output reg  [TKEEP_WIDTH - 1:0] ingress_out_tkeep,
    output reg  [TUSER_WIDTH - 1:0] ingress_out_tuser,
    output reg                      ingress_out_tvalid,
    input  wire                     ingress_out_tready,
    output reg                      ingress_out_tlast,

    output reg                      module_reset,

    input  wire [TDATA_WIDTH - 1:0] egress_in_tdata,
    input  wire [TKEEP_WIDTH - 1:0] egress_in_tkeep,
    input  wire [TUSER_WIDTH - 1:0] egress_in_tuser,
    input  wire                     egress_in_tvalid,
    output reg                      egress_in_tready,
    input  wire                     egress_in_tlast,

    output reg  [TDATA_WIDTH - 1:0] egress_out_tdata,
    output reg  [TKEEP_WIDTH - 1:0] egress_out_tkeep,
    output reg  [TUSER_WIDTH - 1:0] egress_out_tuser,
    output reg                      egress_out_tvalid,
    input  wire                     egress_out_tready,
    output reg                      egress_out_tlast
);

    localparam STATE_INGRESSING = 0;
    localparam STATE_EGRESSING  = 1;
    localparam STATE_RESETTING  = 2;

    reg [1:0] state;
    reg [1:0] state_next;

    always @(*) begin
       ingress_in_tready  = 0;

       ingress_out_tdata  = 0;
       ingress_out_tkeep  = 0;
       ingress_out_tuser  = 0;
       ingress_out_tvalid = 0;
       ingress_out_tlast  = 0;

       if (state == STATE_INGRESSING) begin
           ingress_in_tready  = ingress_out_tready;

           ingress_out_tdata  = ingress_in_tdata;
           ingress_out_tkeep  = ingress_in_tkeep;
           ingress_out_tuser  = ingress_in_tuser;
           ingress_out_tvalid = ingress_in_tvalid;
           ingress_out_tlast  = ingress_in_tlast;
       end
    end

    always @(*) begin
       egress_in_tready  = 0;

       egress_out_tdata  = 0;
       egress_out_tkeep  = 0;
       egress_out_tuser  = 0;
       egress_out_tvalid = 0;
       egress_out_tlast  = 0;

       if (state == STATE_INGRESSING || state == STATE_EGRESSING) begin
           egress_in_tready  = egress_out_tready;

           egress_out_tdata  = egress_in_tdata;
           egress_out_tkeep  = egress_in_tkeep;
           egress_out_tuser  = egress_in_tuser;
           egress_out_tvalid = egress_in_tvalid;
           egress_out_tlast  = egress_in_tlast;
       end
    end

    always @(*) begin
        module_reset = 0;

        if (state == STATE_RESETTING) begin
            module_reset = 1;
        end
    end

    wire ingressing = ingress_out_tvalid && ingress_out_tready;
    wire egressing  = egress_out_tvalid  && egress_out_tready;

    always @(*) begin
        state_next = state;

        if (egressing && egress_out_tlast) begin
            state_next = STATE_RESETTING;
        end else if (ingressing && ingress_out_tlast) begin
            state_next = STATE_EGRESSING;
        end else if (state == STATE_RESETTING) begin
            state_next = STATE_INGRESSING;
        end
    end

    always @(posedge clock) begin
        if (~reset_n) begin
            state <= 0;
        end else begin
            state <= state_next;
        end
    end

endmodule