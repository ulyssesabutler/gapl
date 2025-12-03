module gapl_wrapper
#(
    parameter TDATA_WIDTH  = 256,
    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
) (
    // Global Ports
    input  wire                     axis_aclk,
    input  wire                     axis_resetn,

    // Module input
    input  wire [TDATA_WIDTH - 1:0] packet_body_in_axis_tdata,
    input  wire [TKEEP_WIDTH - 1:0] packet_body_in_axis_tkeep,
    input  wire                     packet_body_in_axis_tvalid,
    output wire                     packet_body_in_axis_tready,
    input  wire                     packet_body_in_axis_tlast,

    // Module output
    output wire [TDATA_WIDTH - 1:0] packet_body_out_axis_tdata,
    output wire [TKEEP_WIDTH - 1:0] packet_body_out_axis_tkeep,
    output wire                     packet_body_out_axis_tvalid,
    input  wire                     packet_body_out_axis_tready,
    output wire                     packet_body_out_axis_tlast
);

    // Module I/O
    wire                     gapl_enable;
    wire                     gapl_reset;

    wire [TDATA_WIDTH - 1:0] gapl_in_tdata;
    wire [TKEEP_WIDTH - 1:0] gapl_in_tkeep;
    wire                     gapl_in_tlast;

    wire [TDATA_WIDTH - 1:0] gapl_out_tdata;
    wire [TKEEP_WIDTH - 1:0] gapl_out_tkeep;
    wire                     gapl_out_tvalid;
    wire                     gapl_out_tlast;

    // Padder I/O
    wire [TDATA_WIDTH - 1:0] padder_in_tdata;
    wire [TKEEP_WIDTH - 1:0] padder_in_tkeep;
    wire                     padder_in_tvalid;
    wire                     padder_in_tready;
    wire                     padder_in_tlast;

    wire [TDATA_WIDTH - 1:0] padder_out_tdata;
    wire [TKEEP_WIDTH - 1:0] padder_out_tkeep;
    wire                     padder_out_tvalid;
    wire                     padder_out_tready;
    wire                     padder_out_tlast;

    // MUX I/O
    wire [TDATA_WIDTH - 1:0] mux_in_tdata;
    wire [TKEEP_WIDTH - 1:0] mux_in_tkeep;
    wire                     mux_in_tvalid;
    wire                     mux_in_tready;
    wire                     mux_in_tlast;

    wire [TDATA_WIDTH - 1:0] mux_out_tdata;
    wire [TKEEP_WIDTH - 1:0] mux_out_tkeep;
    wire                     mux_out_tvalid;
    wire                     mux_out_tready;
    wire                     mux_out_tlast;

    axis_pad_output #( .TDATA_WIDTH(TDATA_WIDTH) ) padder
    (
        .clock(axis_aclk),
        .reset_n(axis_resetn),

        .ingress_in_tdata(packet_body_in_axis_tdata),
        .ingress_in_tkeep(packet_body_in_axis_tkeep),
        .ingress_in_tvalid(packet_body_in_axis_tvalid),
        .ingress_in_tready(packet_body_in_axis_tready),
        .ingress_in_tlast(packet_body_in_axis_tlast),

        .ingress_out_tdata(padder_in_tdata),
        .ingress_out_tkeep(padder_in_tkeep),
        .ingress_out_tvalid(padder_in_tvalid),
        .ingress_out_tready(padder_in_tready),
        .ingress_out_tlast(padder_in_tlast),

        .egress_in_tdata(padder_out_tdata),
        .egress_in_tkeep(padder_out_tkeep),
        .egress_in_tvalid(padder_out_tvalid),
        .egress_in_tready(padder_out_tready),
        .egress_in_tlast(padder_out_tlast),

        .egress_out_tdata(packet_body_out_axis_tdata),
        .egress_out_tkeep(packet_body_out_axis_tkeep),
        .egress_out_tvalid(packet_body_out_axis_tvalid),
        .egress_out_tready(packet_body_out_axis_tready),
        .egress_out_tlast(packet_body_out_axis_tlast)
    );

    axis_mutual_exclusion #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(1)
    ) mutual_exclusion (
        .clock(axis_aclk),
        .reset_n(axis_resetn),

        .ingress_in_tdata(padder_in_tdata),
        .ingress_in_tkeep(padder_in_tkeep),
        .ingress_in_tuser(0),
        .ingress_in_tvalid(padder_in_tvalid),
        .ingress_in_tready(padder_in_tready),
        .ingress_in_tlast(padder_in_tlast),

        .ingress_out_tdata(mux_in_tdata),
        .ingress_out_tkeep(mux_in_tkeep),
        .ingress_out_tuser(),
        .ingress_out_tvalid(mux_in_tvalid),
        .ingress_out_tready(mux_in_tready),
        .ingress_out_tlast(mux_in_tlast),

        .module_reset(gapl_reset),

        .egress_in_tdata(mux_out_tdata),
        .egress_in_tkeep(mux_out_tkeep),
        .egress_in_tuser(0),
        .egress_in_tvalid(mux_out_tvalid),
        .egress_in_tready(mux_out_tready),
        .egress_in_tlast(mux_out_tlast),

        .egress_out_tdata(padder_out_tdata),
        .egress_out_tkeep(padder_out_tkeep),
        .egress_out_tuser(),
        .egress_out_tvalid(padder_out_tvalid),
        .egress_out_tready(padder_out_tready),
        .egress_out_tlast(padder_out_tlast)
    );

    processor_controller #( .TDATA_WIDTH(TDATA_WIDTH) ) controller
    (
        .clock(axis_aclk),
        .reset(~axis_resetn),

        .ingress_in_tdata(mux_in_tdata),
        .ingress_in_tkeep(mux_in_tkeep),
        .ingress_in_tvalid(mux_in_tvalid),
        .ingress_in_tready(mux_in_tready),
        .ingress_in_tlast(mux_in_tlast),

        .ingress_out_tdata(gapl_in_tdata),
        .ingress_out_tkeep(gapl_in_tkeep),
        .ingress_out_tlast(gapl_in_tlast),

        .enable(gapl_enable),

        .egress_in_tdata(gapl_out_tdata),
        .egress_in_tkeep(gapl_out_tkeep),
        .egress_in_tvalid(gapl_out_tvalid),
        .egress_in_tlast(gapl_out_tlast),

        .egress_out_tdata(mux_out_tdata),
        .egress_out_tkeep(mux_out_tkeep),
        .egress_out_tvalid(mux_out_tvalid),
        .egress_out_tready(mux_out_tready),
        .egress_out_tlast(mux_out_tlast)
    );

    // GAPL PROCESSOR

    // Wires
    wire [127:0] key = 128'b0;

    wire [127:0] plaintext_first_half;
    wire [127:0] plaintext_second_half;

    wire [127:0] ciphertext_first_half;
    wire [127:0] ciphertext_second_half;

    // Registers
    reg [TKEEP_WIDTH - 1:0] tkeep_reg_1;
    reg [TKEEP_WIDTH - 1:0] tkeep_reg_2;
    reg [TKEEP_WIDTH - 1:0] tkeep_reg_3;

    reg [TKEEP_WIDTH - 1:0] tkeep_reg_1_next;
    reg [TKEEP_WIDTH - 1:0] tkeep_reg_2_next;
    reg [TKEEP_WIDTH - 1:0] tkeep_reg_3_next;

    reg                     tlast_reg_1;
    reg                     tlast_reg_2;
    reg                     tlast_reg_3;

    reg                     tlast_reg_1_next;
    reg                     tlast_reg_2_next;
    reg                     tlast_reg_3_next;

    always @(*) begin
        tkeep_reg_1_next = tkeep_reg_1;
        tkeep_reg_2_next = tkeep_reg_2;
        tkeep_reg_3_next = tkeep_reg_3;

        tlast_reg_1_next = tlast_reg_1;
        tlast_reg_2_next = tlast_reg_2;
        tlast_reg_3_next = tlast_reg_3;

        if (gapl_enable) begin
            tkeep_reg_1_next = gapl_in_tkeep;
            tkeep_reg_2_next = tkeep_reg_1;
            tkeep_reg_3_next = tkeep_reg_2;

            tlast_reg_1_next = gapl_in_tlast;
            tlast_reg_2_next = tlast_reg_1;
            tlast_reg_3_next = tlast_reg_2;
        end
    end

    always @(posedge axis_aclk) begin
        if (!axis_resetn) begin
            tkeep_reg_1 <= 0;
            tkeep_reg_2 <= 0;
            tkeep_reg_3 <= 0;

            tlast_reg_1 <= 0;
            tlast_reg_2 <= 0;
            tlast_reg_3 <= 0;
        end else begin
            tkeep_reg_1 <= tkeep_reg_1_next;
            tkeep_reg_2 <= tkeep_reg_2_next;
            tkeep_reg_3 <= tkeep_reg_3_next;

            tlast_reg_1 <= tlast_reg_1_next;
            tlast_reg_2 <= tlast_reg_2_next;
            tlast_reg_3 <= tlast_reg_3_next;
        end
    end

    // Connections
    assign plaintext_first_half  = gapl_in_tdata[127:0];
    assign plaintext_second_half = gapl_in_tdata[255:128];

    assign gapl_out_tdata[127:0] = ciphertext_first_half;
    assign gapl_out_tdata[255:128] = ciphertext_second_half;

    assign gapl_out_tkeep = tkeep_reg_3;
    assign gapl_out_tlast = tlast_reg_3;

    assign gapl_out_tvalid = 1;

    packet_body_processor gapl_processor_first_half
    (
        .clock(axis_aclk),
        .reset((!axis_resetn) || gapl_reset),
        .enable(gapl_enable),

        .i(plaintext_first_half),
        .key(key),
        .o(ciphertext_first_half)
    );

    packet_body_processor gapl_processor_second_half
    (
        .clock(axis_aclk),
        .reset((!axis_resetn) || gapl_reset),
        .enable(gapl_enable),

        .i(plaintext_second_half),
        .key(key),
        .o(ciphertext_second_half)
    );

endmodule