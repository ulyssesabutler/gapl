// HLS counterpart of gapl_wrapper.v: the static AXI-Stream boundary around an HLS-generated
// packet_body_processor (see netfpga/hls/common/netfpga_axis.h for that kernel's contract). It is
// packaged as the same gapl_kernel IP with the same outer ports, so the shell can't tell which kind
// of kernel it has - packageCoreGaplKernel picks this file or gapl_wrapper.v per kernel type.
//
// Kept identical to gapl_wrapper.v:
//   - axis_pad_output and axis_mutual_exclusion (one packet in the kernel at a time, and a one-cycle
//     kernel reset after each packet's output tlast), so both kinds of kernel see the same traffic,
//   - reverse_bytes on tdata in both directions (tkeep is not reversed, same as gapl_wrapper.v), so
//     an HLS kernel sees exactly the byte order a GAPL packet_body_processor sees and one
//     test.properties means the same thing to both.
// Dropped: processor_controller. It exists to drive a GAPL kernel's `enable` (GAPL kernels have no
// ready signal of their own); an HLS kernel has real axis tready/tvalid handshakes and applies
// backpressure itself.
module hls_wrapper
#(
    parameter TDATA_WIDTH = 256,
    // `parameter`, not `localparam` - see gapl_wrapper.v for why IP packaging needs this.
    parameter TKEEP_WIDTH = TDATA_WIDTH / 8
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

    wire                     kernel_reset;

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

    // Kernel-side I/O (mutual exclusion <-> kernel)
    wire [TDATA_WIDTH - 1:0] kernel_in_tdata;
    wire [TKEEP_WIDTH - 1:0] kernel_in_tkeep;
    wire                     kernel_in_tvalid;
    wire                     kernel_in_tready;
    wire                     kernel_in_tlast;

    wire [TDATA_WIDTH - 1:0] kernel_out_tdata;
    wire [TKEEP_WIDTH - 1:0] kernel_out_tkeep;
    wire                     kernel_out_tvalid;
    wire                     kernel_out_tready;
    wire                     kernel_out_tlast;

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

        .ingress_out_tdata(kernel_in_tdata),
        .ingress_out_tkeep(kernel_in_tkeep),
        .ingress_out_tuser(),
        .ingress_out_tvalid(kernel_in_tvalid),
        .ingress_out_tready(kernel_in_tready),
        .ingress_out_tlast(kernel_in_tlast),

        .module_reset(kernel_reset),

        .egress_in_tdata(kernel_out_tdata),
        .egress_in_tkeep(kernel_out_tkeep),
        .egress_in_tuser(0),
        .egress_in_tvalid(kernel_out_tvalid),
        .egress_in_tready(kernel_out_tready),
        .egress_in_tlast(kernel_out_tlast),

        .egress_out_tdata(padder_out_tdata),
        .egress_out_tkeep(padder_out_tkeep),
        .egress_out_tuser(),
        .egress_out_tvalid(padder_out_tvalid),
        .egress_out_tready(padder_out_tready),
        .egress_out_tlast(padder_out_tlast)
    );

    wire [TDATA_WIDTH - 1:0] kernel_order_in_tdata;
    wire [TDATA_WIDTH - 1:0] kernel_order_out_tdata;

    reverse_bytes #( .BYTES(TDATA_WIDTH / 8) ) kernel_order_in
    (
        .in(kernel_in_tdata),
        .out(kernel_order_in_tdata)
    );

    reverse_bytes #( .BYTES(TDATA_WIDTH / 8) ) kernel_order_out
    (
        .in(kernel_order_out_tdata),
        .out(kernel_out_tdata)
    );

    // HLS axis interfaces use a synchronous, active-low reset (ap_rst_n).
    packet_body_processor hls_processor
    (
        .ap_clk(axis_aclk),
        .ap_rst_n(axis_resetn && !kernel_reset),

        .i_TDATA(kernel_order_in_tdata),
        .i_TKEEP(kernel_in_tkeep),
        .i_TSTRB(kernel_in_tkeep),
        .i_TVALID(kernel_in_tvalid),
        .i_TREADY(kernel_in_tready),
        .i_TLAST(kernel_in_tlast),

        .o_TDATA(kernel_order_out_tdata),
        .o_TKEEP(kernel_out_tkeep),
        .o_TSTRB(),
        .o_TVALID(kernel_out_tvalid),
        .o_TREADY(kernel_out_tready),
        .o_TLAST(kernel_out_tlast)
    );

endmodule
