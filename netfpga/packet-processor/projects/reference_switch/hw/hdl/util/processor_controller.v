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

    wire [TDATA_WIDTH - 1:0] input_queue_tdata;
    wire [TKEEP_WIDTH - 1:0] input_queue_tkeep;
    wire                     input_queue_tvalid;
    wire                     input_queue_tready;
    wire                     input_queue_tlast;

    axis_queue
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(1)
    ) input_queue (
        .axis_aclk(clock),
        .axis_resetn(~reset),

        .in_tdata(ingress_in_tdata),
        .in_tkeep(ingress_in_tkeep),
        .in_tuser(0),
        .in_tvalid(ingress_in_tvalid),
        .in_tready(ingress_in_tready),
        .in_tlast(ingress_in_tlast),

        .out_tdata(input_queue_tdata),
        .out_tkeep(input_queue_tkeep),
        .out_tuser(),
        .out_tvalid(input_queue_tvalid),
        .out_tready(input_queue_tready),
        .out_tlast(input_queue_tlast)
    );

    wire [TDATA_WIDTH - 1:0] output_queue_tdata;
    wire [TKEEP_WIDTH - 1:0] output_queue_tkeep;
    wire                     output_queue_tvalid;
    wire                     output_queue_tready;
    wire                     output_queue_tlast;

    axis_queue
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(1)
    ) output_queue (
        .axis_aclk(clock),
        .axis_resetn(~reset),

        .in_tdata(output_queue_tdata),
        .in_tkeep(output_queue_tkeep),
        .in_tuser(0),
        .in_tvalid(output_queue_tvalid),
        .in_tready(output_queue_tready),
        .in_tlast(output_queue_tlast),

        .out_tdata(egress_out_tdata),
        .out_tkeep(egress_out_tkeep),
        .out_tuser(),
        .out_tvalid(egress_out_tvalid),
        .out_tready(egress_out_tready),
        .out_tlast(egress_out_tlast)
    );

    // Convinient defintions
    wire reading_in              = input_queue_tvalid & input_queue_tready;
    wire writing_out             = output_queue_tvalid & output_queue_tready;

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
                if (reading_in & input_queue_tlast) state_next = STATE_RUNNING;
            end

            STATE_RUNNING: begin
                if (writing_out & output_queue_tlast) state_next = STATE_STARTING;
            end

        endcase
    end

    // Enabling
    reg enable;

    always @(*) begin
        enable = 0;

        case (state)

            STATE_STARTING: begin
                if (input_queue_tvalid & output_queue_tready) enable = 1;
            end

            STATE_RUNNING: begin
                if (output_queue_tready) enable = 1;
            end

        endcase
    end

    // Hook-up ingress and egress
    assign ingress_out_tdata  = input_queue_tdata;
    assign ingress_out_tkeep  = input_queue_tkeep;
    assign input_queue_tready = enable; // Don't send if the module is disabled
    assign ingress_out_tlast  = input_queue_tlast;

    assign output_queue_tdata   = egress_in_tdata;
    assign output_queue_tkeep   = egress_in_tkeep;
    assign output_queue_tvalid  = egress_in_tvalid & enable;
    assign output_queue_tlast   = egress_in_tlast;

    // Register
    always @(posedge clock) begin
        if (reset) begin
            state <= 0;
        end else begin
            state <= state_next;
        end
    end

endmodule
