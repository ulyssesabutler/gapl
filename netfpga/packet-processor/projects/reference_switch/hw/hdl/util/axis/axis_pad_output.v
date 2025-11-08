module axis_pad_output
#(
    parameter TDATA_WIDTH             = 256,
    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8
)
(
    // Global Ports
    input  wire                     clock,
    input  wire                     reset_n,

    // Module input
    input  wire [TDATA_WIDTH - 1:0] ingress_in_tdata,
    input  wire [TKEEP_WIDTH - 1:0] ingress_in_tkeep,
    input  wire                     ingress_in_tvalid,
    output reg                      ingress_in_tready,
    input  wire                     ingress_in_tlast,

    output reg  [TDATA_WIDTH - 1:0] ingress_out_tdata,
    output reg  [TKEEP_WIDTH - 1:0] ingress_out_tkeep,
    output reg                      ingress_out_tvalid,
    input  wire                     ingress_out_tready,
    output reg                      ingress_out_tlast,

    input  wire [TDATA_WIDTH - 1:0] egress_in_tdata,
    input  wire [TKEEP_WIDTH - 1:0] egress_in_tkeep,
    input  wire                     egress_in_tvalid,
    output reg                      egress_in_tready,
    input  wire                     egress_in_tlast,

    output reg  [TDATA_WIDTH - 1:0] egress_out_tdata,
    output reg  [TKEEP_WIDTH - 1:0] egress_out_tkeep,
    output reg                      egress_out_tvalid,
    input  wire                     egress_out_tready,
    output wire                     egress_out_tlast
);

    // Transmission Tracking
    wire ingressing = ingress_in_tvalid & ingress_in_tready;
    wire egressing  = egress_out_tvalid & egress_out_tready;

    reg  have_seen_last;
    reg  have_seen_last_next;

    wire ingressing_finished = have_seen_last | ingress_in_tlast;

    // Transmission Count
    reg  [31:0] previous_ingress_transmissions;
    reg  [31:0] previous_ingress_transmissions_next;

    reg  [31:0] previous_egress_transmissions;
    reg  [31:0] previous_egress_transmissions_next;

    wire [31:0] ingress_transmissions = previous_ingress_transmissions + ingressing;
    wire [31:0] egress_transmissions  = previous_egress_transmissions  + egressing;

    always @(*) begin
        previous_ingress_transmissions_next = previous_ingress_transmissions;
        previous_egress_transmissions_next  = previous_egress_transmissions;

        if (ingressing) begin
            previous_ingress_transmissions_next = previous_ingress_transmissions + 1;
        end

        if (egressing) begin
            if (egress_out_tlast) begin
                previous_ingress_transmissions_next = 0;
                previous_egress_transmissions_next  = 0;
            end else begin
                previous_egress_transmissions_next = previous_egress_transmissions + 1;
            end
        end
    end

    // Transmission Logic
    wire transmissions_finishing = ingressing_finished && (ingress_transmissions == egress_transmissions);

    // Output
    assign egress_out_tlast = transmissions_finishing;

    // States
    localparam STATE_READING         = 0;
    localparam STATE_WRITING_DATA    = 1;
    localparam STATE_WRITING_PADDING = 2;

    reg [1:0] state;
    reg [1:0] state_next;

    always @(*) begin
        state_next          = state;
        have_seen_last_next = have_seen_last;

        if (ingressing & ingress_in_tlast) begin
            have_seen_last_next = 1;
        end

        if (egressing & egress_out_tlast) begin
            state_next = STATE_READING;
            have_seen_last_next = 0;
        end else if (egressing & egress_in_tlast) begin
            state_next = STATE_WRITING_PADDING;
        end else if (ingressing & ingress_in_tlast) begin
            state_next = STATE_WRITING_DATA;
        end
    end

    // Ingress Handling
    always @(*) begin
        ingress_in_tready  = 0;

        ingress_out_tdata  = 0;
        ingress_out_tkeep  = 0;
        ingress_out_tvalid = 0;
        ingress_out_tlast  = 0;

        case (state)

            STATE_READING: begin
                ingress_in_tready  = ingress_out_tready;

                ingress_out_tdata  = ingress_in_tdata;
                ingress_out_tkeep  = ingress_in_tkeep;
                ingress_out_tvalid = ingress_in_tvalid;
                ingress_out_tlast  = ingress_in_tlast;
            end

        endcase
    end

    // Egress Handling
    always @(*) begin
        egress_in_tready  = 0;

        egress_out_tdata  = 0;
        egress_out_tkeep  = 0;
        egress_out_tvalid = 0;

        case (state)

            STATE_READING: begin
                egress_in_tready  = egress_out_tready;

                egress_out_tdata  = egress_in_tdata;
                egress_out_tkeep  = egress_in_tkeep;
                egress_out_tvalid = egress_in_tvalid;
            end

            STATE_WRITING_DATA: begin
                egress_in_tready  = egress_out_tready;

                egress_out_tdata  = egress_in_tdata;
                egress_out_tkeep  = egress_in_tkeep;
                egress_out_tvalid = egress_in_tvalid;
            end

            STATE_WRITING_PADDING: begin
                egress_in_tready  = 0;

                egress_out_tdata  = 0;
                egress_out_tkeep  = -1;
                egress_out_tvalid = 1;
            end

        endcase
    end

    // Registers
    always @(posedge clock) begin
        if (~reset_n) begin
            state                          <= 0;
            previous_ingress_transmissions <= 0;
            previous_egress_transmissions  <= 0;
            have_seen_last                 <= 0;
        end else begin
            state                          <= state_next;
            previous_ingress_transmissions <= previous_ingress_transmissions_next;
            previous_egress_transmissions  <= previous_egress_transmissions_next;
            have_seen_last                 <= have_seen_last_next;
        end
    end

endmodule