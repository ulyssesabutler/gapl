/* INFERENCE REQUEST PREPROCESSOR
 * ==============================
 * The input to this module will be a stream of network packets that are parts of inference requests.
 * We want to take those packets and process them so the request can be sent directly to the GPU.
 * - Decoding: Converting the JPEG image to a bitmap
 * - Cropping: Removing the edges
 * - Resizing: Scaling the image to the correct size
 * - Tensor Conversion: Convert the bitmap into a neural network input, in the form of a tensor
 */
module packet_processor
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8
)
(
    // Global Ports
    input  wire                axis_aclk,
    input  wire                axis_resetn,

    // Module input
    input  wire [TDATA_WIDTH - 1:0] packet_in_axis_tdata,
    input  wire [TKEEP_WIDTH - 1:0] packet_in_axis_tkeep,
    input  wire [TUSER_WIDTH - 1:0] packet_in_axis_tuser,
    input  wire                     packet_in_axis_tvalid,
    output wire                     packet_in_axis_tready,
    input  wire                     packet_in_axis_tlast,

    // Module output
    output wire [TDATA_WIDTH - 1:0] packet_out_axis_tdata,
    output wire [TKEEP_WIDTH - 1:0] packet_out_axis_tkeep,
    output wire [TUSER_WIDTH - 1:0] packet_out_axis_tuser,
    output wire                     packet_out_axis_tvalid,
    input  wire                     packet_out_axis_tready,
    output wire                     packet_out_axis_tlast
);

    wire [TDATA_WIDTH - 1:0] mux_in_tdata;
    wire [TKEEP_WIDTH - 1:0] mux_in_tkeep;
    wire [TUSER_WIDTH - 1:0] mux_in_tuser;
    wire                     mux_in_tvalid;
    reg                      mux_in_tready;
    wire                     mux_in_tlast;

    reg  [TDATA_WIDTH - 1:0] mux_out_tdata;
    reg  [TKEEP_WIDTH - 1:0] mux_out_tkeep;
    reg  [TUSER_WIDTH - 1:0] mux_out_tuser;
    reg                      mux_out_tvalid;
    wire                     mux_out_tready;
    reg                      mux_out_tlast;

    axis_mutual_exclusion #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    ) mutual_exclusion (
        .clock(axis_aclk),
        .reset_n(axis_resetn),

        .ingress_in_tdata(packet_in_axis_tdata),
        .ingress_in_tkeep(packet_in_axis_tkeep),
        .ingress_in_tuser(packet_in_axis_tuser),
        .ingress_in_tvalid(packet_in_axis_tvalid),
        .ingress_in_tready(packet_in_axis_tready),
        .ingress_in_tlast(packet_in_axis_tlast),

        .ingress_out_tdata(mux_in_tdata),
        .ingress_out_tkeep(mux_in_tkeep),
        .ingress_out_tuser(mux_in_tuser),
        .ingress_out_tvalid(mux_in_tvalid),
        .ingress_out_tready(mux_in_tready),
        .ingress_out_tlast(mux_in_tlast),

        .module_reset(),

        .egress_in_tdata(mux_out_tdata),
        .egress_in_tkeep(mux_out_tkeep),
        .egress_in_tuser(mux_out_tuser),
        .egress_in_tvalid(mux_out_tvalid),
        .egress_in_tready(mux_out_tready),
        .egress_in_tlast(mux_out_tlast),

        .egress_out_tdata(packet_out_axis_tdata),
        .egress_out_tkeep(packet_out_axis_tkeep),
        .egress_out_tuser(packet_out_axis_tuser),
        .egress_out_tvalid(packet_out_axis_tvalid),
        .egress_out_tready(packet_out_axis_tready),
        .egress_out_tlast(packet_out_axis_tlast)
    );

    reg  [TDATA_WIDTH - 1:0] gapl_in_tdata;
    reg  [TKEEP_WIDTH - 1:0] gapl_in_tkeep;
    reg                      gapl_in_tvalid;
    wire                     gapl_in_tready;
    reg                      gapl_in_tlast;

    wire [TDATA_WIDTH - 1:0] gapl_out_tdata;
    wire [TKEEP_WIDTH - 1:0] gapl_out_tkeep;
    wire                     gapl_out_tvalid;
    reg                      gapl_out_tready;
    wire                     gapl_out_tlast;

    gapl_wrapper #( .TDATA_WIDTH(TDATA_WIDTH) ) gapl
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .packet_body_in_axis_tdata(gapl_in_tdata),
        .packet_body_in_axis_tkeep(gapl_in_tkeep),
        .packet_body_in_axis_tvalid(gapl_in_tvalid),
        .packet_body_in_axis_tready(gapl_in_tready),
        .packet_body_in_axis_tlast(gapl_in_tlast),

        .packet_body_out_axis_tdata(gapl_out_tdata),
        .packet_body_out_axis_tkeep(gapl_out_tkeep),
        .packet_body_out_axis_tvalid(gapl_out_tvalid),
        .packet_body_out_axis_tready(gapl_out_tready),
        .packet_body_out_axis_tlast(gapl_out_tlast)
    );

    // Transmission Counter
    reg [31:0] transmission_count;
    reg [31:0] transmission_count_next;

    wire transmitting = mux_out_tvalid & mux_out_tready;

    always @(*) begin
        transmission_count_next = transmission_count;

        if (transmitting) begin
            if (mux_out_tlast) begin
                transmission_count_next = 0;
            end else begin
                transmission_count_next = transmission_count + 1;
            end
        end
    end

    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            transmission_count <= 0;
        end else begin
            transmission_count <= transmission_count_next;
        end
    end

    // Hooker upper
    always @(*) begin
        gapl_in_tdata  = 0;
        gapl_in_tkeep  = 0;
        gapl_in_tvalid = 0;
        gapl_in_tlast  = 0;

        mux_out_tdata  = mux_in_tdata;
        mux_out_tkeep  = mux_in_tkeep;
        mux_out_tuser  = mux_in_tuser;
        mux_out_tvalid = mux_in_tvalid;
        mux_out_tlast  = mux_in_tlast;

        mux_in_tready  = mux_out_tready;
        gapl_out_tready        = 0;

        if (transmission_count >= 2) begin
            gapl_in_tdata  = mux_in_tdata;
            gapl_in_tkeep  = mux_in_tkeep;
            gapl_in_tvalid = mux_in_tvalid;
            gapl_in_tlast  = mux_in_tlast;

            mux_out_tdata  = gapl_out_tdata;
            mux_out_tkeep  = gapl_out_tkeep;
            mux_out_tuser  = 0;
            mux_out_tvalid = gapl_out_tvalid;
            mux_out_tlast  = gapl_out_tlast;

            mux_in_tready  = gapl_in_tready;
            gapl_out_tready        = mux_out_tready;
        end
    end

endmodule