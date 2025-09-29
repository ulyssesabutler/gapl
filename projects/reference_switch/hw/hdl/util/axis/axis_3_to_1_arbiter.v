/* BITMAP TO TENSOR
 * ================
 * This module takes a single AXI-Stream as an input, and replicates that same stream 3 times as an
 * output.
 */
module axis_3_to_1_arbiter
#(
    parameter TDATA_WIDTH  = 256,
    parameter TUSER_WIDTH  = 128,

    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
)
(
    // Global Ports
    input                      axis_aclk,
    input                      axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0] axis_input_0_tdata,
    input  [TKEEP_WIDTH - 1:0] axis_input_0_tkeep,
    input  [TUSER_WIDTH - 1:0] axis_input_0_tuser,
    input                      axis_input_0_tvalid,
    output                     axis_input_0_tready,
    input                      axis_input_0_tlast,

    input  [TDATA_WIDTH - 1:0] axis_input_1_tdata,
    input  [TKEEP_WIDTH - 1:0] axis_input_1_tkeep,
    input  [TUSER_WIDTH - 1:0] axis_input_1_tuser,
    input                      axis_input_1_tvalid,
    output                     axis_input_1_tready,
    input                      axis_input_1_tlast,

    input  [TDATA_WIDTH - 1:0] axis_input_2_tdata,
    input  [TKEEP_WIDTH - 1:0] axis_input_2_tkeep,
    input  [TUSER_WIDTH - 1:0] axis_input_2_tuser,
    input                      axis_input_2_tvalid,
    output                     axis_input_2_tready,
    input                      axis_input_2_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] axis_output_tdata,
    output [TKEEP_WIDTH - 1:0] axis_output_tkeep,
    output [TUSER_WIDTH - 1:0] axis_output_tuser,
    output                     axis_output_tvalid,
    input                      axis_output_tready,
    output                     axis_output_tlast
);  

    // Output Queue
    reg  [TDATA_WIDTH - 1:0]          output_queue_input_tdata;
    reg  [((TDATA_WIDTH / 8)) - 1:0]  output_queue_input_tkeep;
    reg  [TUSER_WIDTH-1:0]            output_queue_input_tuser;
    reg                               output_queue_input_tlast;

    reg                               write_to_output_queue;
    wire                              read_from_output_queue;

    wire                              output_queue_nearly_full;
    wire                              output_queue_empty;

    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH+TUSER_WIDTH+TDATA_WIDTH/8+1), // Fit the whole AXIS packet and the headers
        .MAX_DEPTH_BITS(4)
    )
    output_queue
    (
        .din         ({output_queue_input_tdata, output_queue_input_tkeep, output_queue_input_tuser, output_queue_input_tlast}),
        .wr_en       (write_to_output_queue),
        .rd_en       (read_from_output_queue),
        .dout        ({axis_output_tdata, axis_output_tkeep, axis_output_tuser, axis_output_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (output_queue_nearly_full),
        .empty       (output_queue_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    assign read_from_output_queue = axis_output_tvalid & axis_output_tready;
    assign axis_output_tvalid = ~output_queue_empty;

    // AXI Packet Input
    wire [TDATA_WIDTH - 1:0]         axis_in_tdata;
    wire [((TDATA_WIDTH / 8)) - 1:0] axis_in_tkeep;
    wire [TUSER_WIDTH-1:0]           axis_in_tuser;
    wire                             axis_in_tvalid;
    wire                             axis_in_tready;
    wire                             axis_in_tlast;

    wire reading_axis_packet = axis_in_tvalid & axis_in_tready;
    assign axis_in_tready = ~output_queue_nearly_full;

    // FSM
    reg [1:0] current_input;
    reg [1:0] current_input_next;

    assign axis_in_tdata = current_input == 0 ? axis_input_0_tdata : current_input == 1 ? axis_input_1_tdata : axis_input_2_tdata;
    assign axis_in_tkeep = current_input == 0 ? axis_input_0_tkeep : current_input == 1 ? axis_input_1_tkeep : axis_input_2_tkeep;
    assign axis_in_tuser = current_input == 0 ? axis_input_0_tuser : current_input == 1 ? axis_input_1_tuser : axis_input_2_tuser;
    assign axis_in_tlast = current_input == 0 ? axis_input_0_tlast : current_input == 1 ? axis_input_1_tlast : axis_input_2_tlast;

    assign axis_in_tvalid = current_input == 0 ? axis_input_0_tvalid : current_input == 1 ? axis_input_1_tvalid : axis_input_2_tvalid;

    assign axis_input_0_tready = current_input == 0 ? axis_in_tready : 0;
    assign axis_input_1_tready = current_input == 1 ? axis_in_tready : 0;
    assign axis_input_2_tready = current_input == 2 ? axis_in_tready : 0;

    always @(*) begin
        current_input_next = current_input;

        if (reading_axis_packet & axis_in_tlast) begin
            if (current_input == 2)
                current_input_next = 0;
            else
                current_input_next = current_input + 1;
        end
    end

    always @(posedge axis_aclk) begin
        if (~axis_resetn)
            current_input <= 0;
        else
            current_input <= current_input_next;
    end

    // AXI Packet Output
    reg  [TDATA_WIDTH - 1:0]          output_queue_input_tdata_next;
    reg  [((TDATA_WIDTH / 8)) - 1:0]  output_queue_input_tkeep_next;
    reg  [TUSER_WIDTH-1:0]            output_queue_input_tuser_next;
    reg                               output_queue_input_tlast_next;

    reg                               write_to_output_queue_next;

    always @(*) begin
        output_queue_input_tdata_next = axis_in_tdata;
        output_queue_input_tkeep_next = axis_in_tkeep;
        output_queue_input_tuser_next = axis_in_tuser;

        if (current_input != 2)
            output_queue_input_tlast_next = 0;
        else
            output_queue_input_tlast_next = axis_in_tlast;

        write_to_output_queue_next = 0;

        if (reading_axis_packet) write_to_output_queue_next = 1;
    end

    always @(posedge axis_aclk) begin
        output_queue_input_tdata <= output_queue_input_tdata_next;
        output_queue_input_tkeep <= output_queue_input_tkeep_next;
        output_queue_input_tuser <= output_queue_input_tuser_next;
        output_queue_input_tlast <= output_queue_input_tlast_next;

        write_to_output_queue <= write_to_output_queue_next;
    end

endmodule