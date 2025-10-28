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

    localparam MAC_ADDRESS_WIDTH      = 48,
    localparam IP_ADDRESS_WIDTH       = 32,
    localparam IP_LENGTH_WIDTH        = 16,
    localparam IP_ID_WIDTH            = 16,
    localparam PORT_WIDTH             = 16,
    localparam UDP_LENGTH_WIDTH       = 16,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8
)
(
    // Global Ports
    input                      axis_aclk,
    input                      axis_resetn,

    // Module input
    input  wire [TDATA_WIDTH - 1:0] packet_in_axis_tdata,
    input  wire [TKEEP_WIDTH - 1:0] packet_in_axis_tkeep,
    input  wire [TUSER_WIDTH - 1:0] packet_in_axis_tuser,
    input  wire                     packet_in_axis_tvalid,
    output reg                      packet_in_axis_tready,
    input  wire                     packet_in_axis_tlast,

    // Module output
    output reg  [TDATA_WIDTH - 1:0] packet_out_axis_tdata,
    output reg  [TKEEP_WIDTH - 1:0] packet_out_axis_tkeep,
    output reg  [TUSER_WIDTH - 1:0] packet_out_axis_tuser,
    output reg                      packet_out_axis_tvalid,
    input  wire                     packet_out_axis_tready,
    output reg                      packet_out_axis_tlast
);

    reg  [TDATA_WIDTH - 1:0] gapl_in_tdata;
    reg  [TKEEP_WIDTH - 1:0] gapl_in_tkeep;
    reg  [TUSER_WIDTH - 1:0] gapl_in_tuser;
    reg                      gapl_in_tvalid;
    wire                     gapl_in_tready;
    reg                      gapl_in_tlast;

    wire [TDATA_WIDTH - 1:0] gapl_out_tdata;
    wire [TKEEP_WIDTH - 1:0] gapl_out_tkeep;
    wire [TUSER_WIDTH - 1:0] gapl_out_tuser;
    wire                     gapl_out_tvalid;
    reg                      gapl_out_tready;
    wire                     gapl_out_tlast;

    gapl_wrapper
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    gapl
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .packet_body_in_axis_tdata(gapl_in_tdata),
        .packet_body_in_axis_tkeep(gapl_in_tkeep),
        .packet_body_in_axis_tuser(gapl_in_tuser),
        .packet_body_in_axis_tvalid(gapl_in_tvalid),
        .packet_body_in_axis_tready(gapl_in_tready),
        .packet_body_in_axis_tlast(gapl_in_tlast),

        .packet_body_out_axis_tdata(gapl_out_tdata),
        .packet_body_out_axis_tkeep(gapl_out_tkeep),
        .packet_body_out_axis_tuser(gapl_out_tuser),
        .packet_body_out_axis_tvalid(gapl_out_tvalid),
        .packet_body_out_axis_tready(gapl_out_tready),
        .packet_body_out_axis_tlast(gapl_out_tlast)
    );

    // Transmission Counter
    reg [31:0] transmission_count;
    reg [31:0] transmission_count_next;

    wire transmitting = packet_in_axis_tvalid & packet_in_axis_tready;

    always @(*) begin
        transmission_count_next = transmission_count;

        if (transmitting) begin
            if (packet_in_axis_tlast) begin
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
        gapl_in_tuser  = 0;
        gapl_in_tvalid = 0;
        gapl_in_tlast  = 0;

        packet_out_axis_tdata  = packet_in_axis_tdata;
        packet_out_axis_tkeep  = packet_in_axis_tkeep;
        packet_out_axis_tuser  = packet_in_axis_tuser;
        packet_out_axis_tvalid = packet_in_axis_tvalid;
        packet_out_axis_tlast  = packet_in_axis_tlast;

        packet_in_axis_tready  = packet_out_axis_tready;
        gapl_out_tready        = 0;

        if (transmission_count >= 2) begin
            gapl_in_tdata  = packet_in_axis_tdata;
            gapl_in_tkeep  = packet_in_axis_tkeep;
            gapl_in_tuser  = packet_in_axis_tuser;
            gapl_in_tvalid = packet_in_axis_tvalid;
            gapl_in_tlast  = packet_in_axis_tlast;

            packet_out_axis_tdata  = gapl_out_tdata;
            packet_out_axis_tkeep  = gapl_out_tkeep;
            packet_out_axis_tuser  = gapl_out_tuser;
            packet_out_axis_tvalid = gapl_out_tvalid;
            packet_out_axis_tlast  = gapl_out_tlast;

            packet_in_axis_tready  = gapl_in_tready;
            gapl_out_tready        = packet_out_axis_tready;
        end
    end

endmodule