module axis_user_tracker
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

    output wire [TDATA_WIDTH - 1:0] ingress_out_tdata,
    output wire [TKEEP_WIDTH - 1:0] ingress_out_tkeep,
    output reg                      ingress_out_tvalid,
    input  wire                     ingress_out_tready,
    output wire                     ingress_out_tlast,

    input  wire [TDATA_WIDTH - 1:0] egress_in_tdata,
    input  wire [TKEEP_WIDTH - 1:0] egress_in_tkeep,
    input  wire                     egress_in_tvalid,
    output wire                     egress_in_tready,
    input  wire                     egress_in_tlast,

    output wire [TDATA_WIDTH - 1:0] egress_out_tdata,
    output wire [TKEEP_WIDTH - 1:0] egress_out_tkeep,
    output reg  [TUSER_WIDTH - 1:0] egress_out_tuser,
    output reg                      egress_out_tvalid,
    input  wire                     egress_out_tready,
    output wire                     egress_out_tlast
);

    wire [TDATA_WIDTH - 1:0] output_queue_tdata;
    wire [TKEEP_WIDTH - 1:0] output_queue_tkeep;
    wire [TUSER_WIDTH - 1:0] output_queue_tuser;
    wire                     output_queue_tvalid;
    reg                      output_queue_tready;
    wire                     output_queue_tlast;

    axis_queue
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    ) output_queue (
        .axis_aclk(clock),
        .axis_resetn(reset_n),

        .in_tdata(egress_in_tdata),
        .in_tkeep(egress_in_tkeep),
        .in_tuser(egress_in_tuser),
        .in_tvalid(egress_in_tvalid),
        .in_tready(egress_in_tready),
        .in_tlast(egress_in_tlast),

        .out_tdata(output_queue_tdata),
        .out_tkeep(output_queue_tkeep),
        .out_tuser(output_queue_tuser),
        .out_tvalid(output_queue_tvalid),
        .out_tready(output_queue_tready),
        .out_tlast(output_queue_tlast)
    );

    assign ingress_out_tdata = ingress_in_tdata;
    assign ingress_out_tkeep = ingress_in_tkeep;
    assign ingress_out_tlast = ingress_in_tlast;

    assign egress_out_tdata  = output_queue_tdata;
    assign egress_out_tkeep  = output_queue_tkeep;
    assign egress_out_tlast  = output_queue_tlast;

    // Ingress State
    localparam INGRESS_STATE_WAITING  = 0;
    localparam INGRESS_STATE_RUNNING  = 1;
    localparam INGRESS_STATE_FINISHED = 2;

    reg [1:0] ingress_state;
    reg [1:0] ingress_state_next;

    // Egress State
    localparam EGRESS_STATE_WAITING  = 0;
    localparam EGRESS_STATE_RUNNING  = 1;

    reg [1:0] egress_state;
    reg [1:0] egress_state_next;


    // User Buffer
    reg [TUSER_WIDTH - 1:0] tuser_buffer;
    reg [TUSER_WIDTH - 1:0] tuser_buffer_next;

    reg buffer_written;
    reg buffer_written_next;

    reg buffer_read;
    reg buffer_read_next;

    // Transmission Tracking
    wire ingressing = ingress_in_tvalid & ingress_in_tready;
    wire egressing  = egress_out_tvalid & egress_out_tready;

    wire ingress_finishing = ingressing & ingress_in_tlast;
    wire egress_finishing  = egressing & egress_out_tlast;

    // User Buffer Logic
    always @(*) begin
        buffer_written_next = buffer_written;
        buffer_read_next    = buffer_read;
        tuser_buffer_next   = tuser_buffer;

        if ((ingress_state == INGRESS_STATE_WAITING) & ingressing) begin
            tuser_buffer_next   = ingress_in_tuser;
            buffer_written_next = 1;
        end

        if ((egress_state == EGRESS_STATE_WAITING) & egressing) begin
            buffer_read_next    = 1;
        end

        if (egress_finishing) begin
            tuser_buffer_next   = 0;
            buffer_written_next = 0;
            buffer_read_next    = 0;
        end
    end

    // Ingress State Logic
    always @(*) begin
        ingress_state_next = ingress_state;

        if (egress_finishing) begin
            ingress_state_next = INGRESS_STATE_WAITING;
        end else if (ingress_finishing) begin
            ingress_state_next = INGRESS_STATE_FINISHED;
        end else if ((ingress_state == INGRESS_STATE_WAITING) & ingressing) begin
            ingress_state_next = INGRESS_STATE_RUNNING;
        end

    end

    // Egress State Logic
    always @(*) begin
        egress_state_next = egress_state;

        if (egress_finishing) begin
            egress_state_next = EGRESS_STATE_WAITING;
        end else if ((egress_state == EGRESS_STATE_WAITING) & egressing) begin
            egress_state_next = EGRESS_STATE_RUNNING;
        end
    end

    // Handshake Logic
    always @(*) begin
        ingress_out_tvalid = 0;
        ingress_in_tready  = 0;

        output_queue_tready   = 0;
        egress_out_tvalid  = 0;

        if ((ingress_state == INGRESS_STATE_WAITING) || (ingress_state == INGRESS_STATE_RUNNING)) begin
            ingress_out_tvalid = ingress_in_tvalid;
            ingress_in_tready  = ingress_out_tready;
        end

        if (egress_state == EGRESS_STATE_WAITING) begin
            if (buffer_written) begin
                output_queue_tready  = egress_out_tready;
                egress_out_tvalid = output_queue_tvalid;
            end
        end else if (egress_state == EGRESS_STATE_RUNNING) begin
            output_queue_tready  = egress_out_tready;
            egress_out_tvalid = output_queue_tvalid;
        end
    end

    // User Egress Logic
    always @(*) begin
        egress_out_tuser = 0;

        if (egress_state == EGRESS_STATE_WAITING) begin
            egress_out_tuser = tuser_buffer;
        end
    end

    // Registers
    always @(posedge clock) begin
        if (!reset_n) begin
            buffer_written <= 0;
            buffer_read    <= 0;

            tuser_buffer   <= 0;

            ingress_state  <= 0;
            egress_state   <= 0;
        end else begin
            buffer_written <= buffer_written_next;
            buffer_read    <= buffer_read_next;

            tuser_buffer   <= tuser_buffer_next;

            ingress_state  <= ingress_state_next;
            egress_state   <= egress_state_next;
        end
    end

endmodule